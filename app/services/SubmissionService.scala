/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import javax.inject.{Inject, Singleton}

import audit.{DesSubmissionAuditEventDetail, DesSubmissionEvent, DesTopUpAuditEventDetail, DesTopUpEvent}
import common.exceptions.DBExceptions.MissingRegDocument
import common.exceptions.RegistrationExceptions._
import common.exceptions.SubmissionExceptions._
import config.MicroserviceAuditConnector
import connectors.{AuthConnect, AuthConnector, BusinessRegistrationConnect, BusinessRegistrationConnector, DESConnect, DESConnector, IncorporationInformationConnect, IncorporationInformationConnector}
import enums.PAYEStatus
import models._
import models.incorporation.IncorpStatusUpdate
import models.submission._
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc.{AnyContent, Request}
import repositories._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NoStackTrace

class RejectedIncorporationException(msg: String) extends NoStackTrace {
  override def getMessage: String = msg
}

@Singleton
class SubmissionService @Inject()(injSequenceMongoRepository: SequenceMongo,
                                  injRegistrationMongoRepository: RegistrationMongo,
                                  injDESConnector: DESConnector,
                                  injIncorprorationInformationConnector: IncorporationInformationConnector,
                                  injAuthConnector: AuthConnector,
                                  injBusinessRegistrationConnector: BusinessRegistrationConnector) extends SubmissionSrv {
  val sequenceRepository = injSequenceMongoRepository.store
  val registrationRepository = injRegistrationMongoRepository.store
  val desConnector = injDESConnector
  val incorporationInformationConnector = injIncorprorationInformationConnector
  val authConnector = injAuthConnector
  val auditConnector = MicroserviceAuditConnector
  val businessRegistrationConnector = injBusinessRegistrationConnector
}

trait SubmissionSrv {

  val sequenceRepository: SequenceRepository
  val registrationRepository: RegistrationRepository
  val desConnector: DESConnect
  val incorporationInformationConnector: IncorporationInformationConnect
  val authConnector: AuthConnect
  val auditConnector: AuditConnector
  val businessRegistrationConnector: BusinessRegistrationConnect

  private val REGIME = "paye"
  private val SUBSCRIBER = "SCRS"
  private val CALLBACK_URL = controllers.routes.RegistrationController.processIncorporationData().url
  private val rejected = "rejected"

  def submitToDes(regId: String)(implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[String] = {
    for {
      ackRef      <- assertOrGenerateAcknowledgementReference(regId)
      incUpdate   <- getIncorporationUpdate(regId) recover {
        case ex: RejectedIncorporationException =>
          updatePAYERegistrationDocument(regId, PAYEStatus.cancelled)
          throw ex
      }
      submission  <- buildADesSubmission(regId, incUpdate)
      _           <- desConnector.submitToDES(submission)
      _           <- auditDESSubmission(regId, incUpdate.fold("partial")(_ => "full"), Json.toJson[DESSubmission](submission).as[JsObject])
      updatedStatus = incUpdate.fold(PAYEStatus.held)(_ => PAYEStatus.submitted)
      _           <- updatePAYERegistrationDocument(regId, updatedStatus)
    } yield ackRef
  }

  def submitTopUpToDES(regId: String, incorpStatusUpdate: IncorpStatusUpdate)(implicit hc: HeaderCarrier): Future[PAYEStatus.Value] = {
    for {
      desSubmission <- buildTopUpDESSubmission(regId, incorpStatusUpdate)
      _             <- desConnector.submitToDES(desSubmission)
      _             <- auditDESTopUp(regId, Json.toJson[DESSubmission](desSubmission).as[JsObject])
      updatedStatus = if(incorpStatusUpdate.status == rejected) PAYEStatus.cancelled else PAYEStatus.submitted
      status        <- updatePAYERegistrationDocument(regId, updatedStatus)
    } yield status
  }

  def getIncorporationUpdate(regId: String)(implicit hc: HeaderCarrier): Future[Option[IncorpStatusUpdate]] = {
    for {
      txId      <- registrationRepository.retrieveTransactionId(regId)
      incStatus <- incorporationInformationConnector.getIncorporationUpdate(txId, REGIME, SUBSCRIBER, CALLBACK_URL) map {
        case Some(update) if update.status == rejected => throw new RejectedIncorporationException(s"incorporation for regId $regId has been rejected")
        case response => response
      }
    } yield incStatus
  }

  private[services] def assertOrGenerateAcknowledgementReference(regId: String): Future[String]= {
    registrationRepository.retrieveAcknowledgementReference(regId) flatMap {
      case Some(ackRef) => Future.successful(ackRef)
      case None => for {
        newAckref <- generateAcknowledgementReference
        _ <- registrationRepository.saveAcknowledgementReference(regId, newAckref)
      } yield newAckref
    }
  }

  private[services] def generateAcknowledgementReference: Future[String] = {
    val sequenceID = "AcknowledgementID"
    sequenceRepository.getNext(sequenceID)
      .map(ref => f"BRPY$ref%011d")
  }

  private[services] def buildADesSubmission(regId: String, incorpStatusUpdate: Option[IncorpStatusUpdate])(implicit hc: HeaderCarrier): Future[DESSubmissionModel] = {
    registrationRepository.retrieveRegistration(regId) flatMap {
      case Some(payeReg) if payeReg.status == PAYEStatus.draft => incorpStatusUpdate match {
        case Some(statusUpdate) =>
          Logger.info("[SubmissionService] - [buildPartialOrFullDesSubmission]: building a full DES submission")
          payeReg2DESSubmission(payeReg, DateTime.now(DateTimeZone.UTC), statusUpdate.crn)
        case None =>
          Logger.info("[SubmissionService] - [buildPartialOrFullDesSubmission]: building a partial DES submission")
          payeReg2DESSubmission(payeReg, DateTime.now(DateTimeZone.UTC), None)
      }
      case Some(payeReg) if payeReg.status == PAYEStatus.invalid => throw new InvalidRegistrationException(regId)
      case None =>
        Logger.warn(s"[SubmissionService] - [buildPartialDesSubmission]:  building des top submission failed, there was no registration document present for regId $regId")
        throw new MissingRegDocument(regId)
      case _ =>
        Logger.warn(s"[SubmissionService] - [buildPartialDesSubmission]: The registration for regId $regId has already been submitted")
        throw new RegistrationAlreadySubmitted(regId)
    }
  }

  private[services] def buildTopUpDESSubmission(regId: String, incorpStatusUpdate: IncorpStatusUpdate): Future[TopUpDESSubmission] = {
    registrationRepository.retrieveRegistration(regId) map {
      case Some(payeReg) if payeReg.status == PAYEStatus.held => payeReg2TopUpDESSubmission(payeReg, incorpStatusUpdate)
      case Some(payeReg) =>
        Logger.error(s"[SubmissionService] - [buildTopUpDESSubmission]: paye status is currently ${payeReg.status} for registrationId $regId")
        throw new InvalidRegistrationException(regId)
      case None =>
        Logger.error(s"[SubmissionService] - [buildTopUpDESSubmission]: building des top submission failed, there was no registration document present for regId $regId")
        throw new MissingRegDocument(regId)
    }
  }

  private def updatePAYERegistrationDocument(regId: String, newStatus: PAYEStatus.Value): Future[PAYEStatus.Value] = {
    registrationRepository.updateRegistrationStatus(regId, newStatus) map {
      _ => if(!newStatus.equals(PAYEStatus.cancelled)) {registrationRepository.cleardownRegistration(regId)}
        newStatus
    }
  }

  private[services] def payeReg2DESSubmission(payeReg: PAYERegistration, timestamp: DateTime, incorpUpdateCrn: Option[String])(implicit hc: HeaderCarrier): Future[DESSubmissionModel] = {
    val companyDetails = payeReg.companyDetails.getOrElse {
      throw new CompanyDetailsNotDefinedException
    }

    val ackRef = payeReg.acknowledgementReference.getOrElse {
      Logger.warn(s"[SubmissionService] - [payeReg2PartialDESSubmission]: Unable to convert to Partial DES Submission model for reg ID ${payeReg.registrationID}, Error: Missing Acknowledgement Ref")
      throw new AcknowledgementReferenceNotExistsException(payeReg.registrationID)
    }

    buildDESMetaData(payeReg.registrationID, timestamp, payeReg.completionCapacity) map {
      desMetaData => {
        DESSubmissionModel(
          acknowledgementReference = ackRef,
          metaData = desMetaData,
          limitedCompany = buildDESLimitedCompany(companyDetails, payeReg.sicCodes, incorpUpdateCrn, payeReg.directors, payeReg.employment),
          employingPeople = buildDESEmployingPeople(payeReg.registrationID, payeReg.employment, payeReg.payeContact)
        )
      }
    }
  }

  private[services] def payeReg2TopUpDESSubmission(payeReg: PAYERegistration, incorpStatusUpdate: IncorpStatusUpdate): TopUpDESSubmission = {
    TopUpDESSubmission(
      acknowledgementReference = payeReg.acknowledgementReference.getOrElse {
        Logger.warn(s"[SubmissionService] - [payeReg2TopUpDESSubmission]: Unable to convert to Top Up DES Submission model for reg ID ${payeReg.registrationID}, Error: Missing Acknowledgement Ref")
        throw new AcknowledgementReferenceNotExistsException(payeReg.registrationID)
      },
      status = incorpStatusUpdate.status,
      crn = incorpStatusUpdate.crn
    )
  }

  private[services] def buildNatureOfBusiness(sicCodes: Seq[SICCode]): String = {
    sicCodes match {
      case s if s == Seq.empty => throw new SICCodeNotDefinedException
      case s => s.head.description.getOrElse(throw new SICCodeNotDefinedException)
    }
  }

  private[services] def buildDESCompletionCapacity(capacity: Option[String]): DESCompletionCapacity = {
    val DIRECTOR = "director"
    val AGENT = "agent"
    val OTHER = "other"
    capacity.map(_.trim.toLowerCase).map {
        case DIRECTOR => DESCompletionCapacity(DIRECTOR, None)
        case AGENT => DESCompletionCapacity(AGENT, None)
        case other => DESCompletionCapacity(OTHER, Some(other))
      }.getOrElse{
        throw new CompletionCapacityNotDefinedException
      }
  }

  private[services] def buildDESMetaData(regId: String, timestamp: DateTime, completionCapacity: Option[String])(implicit hc: HeaderCarrier): Future[DESMetaData] = {
    for {
      language <- retrieveLanguage(regId)
      credId <- retrieveCredId
      sessionId = retrieveSessionID(hc)
    } yield {
      DESMetaData(sessionId, credId, language, timestamp, buildDESCompletionCapacity(completionCapacity))
    }
  }

  private[services] def buildDESLimitedCompany(companyDetails: CompanyDetails,
                                               sicCodes: Seq[SICCode],
                                               incorpUpdateCrn: Option[String],
                                               directors: Seq[Director],
                                               employment: Option[Employment]): DESLimitedCompany = {
    DESLimitedCompany(
      companyUTR = None,
      companiesHouseCompanyName = companyDetails.companyName,
      nameOfBusiness = companyDetails.tradingName,
      businessAddress = companyDetails.ppobAddress,
      businessContactDetails = companyDetails.businessContactDetails,
      natureOfBusiness = buildNatureOfBusiness(sicCodes),
      crn = incorpUpdateCrn,
      directors = directors,
      registeredOfficeAddress = companyDetails.roAddress,
      operatingOccPensionScheme = employment.getOrElse(throw new EmploymentDetailsNotDefinedException).companyPension
    )
  }

  private[services] def buildDESEmployingPeople(regId: String, employment: Option[Employment], payeContact: Option[PAYEContact]): DESEmployingPeople = {
    val employmentDetails = employment.getOrElse(throw new EmploymentDetailsNotDefinedException)
    val payeContactDetails = payeContact.getOrElse(throw new PAYEContactNotDefinedException)

    DESEmployingPeople(
      dateOfFirstEXBForEmployees = employmentDetails.firstPaymentDate,
      numberOfEmployeesExpectedThisYear = if( employmentDetails.employees ) "1" else "0",
      engageSubcontractors = employmentDetails.subcontractors,
      correspondenceName = payeContactDetails.contactDetails.name,
      correspondenceContactDetails = payeContactDetails.contactDetails.digitalContactDetails,
      payeCorrespondenceAddress = payeContactDetails.correspondenceAddress
    )
  }

  private[services] def auditDESSubmission(regId: String, desSubmissionState: String, jsSubmission: JsObject)(implicit hc: HeaderCarrier, req: Request[AnyContent]) = {
    for {
      authority <- authConnector.getCurrentAuthority
      userDetails <- authConnector.getUserDetails
      authProviderId = userDetails.get.authProviderId
    } yield {
      val event = new DesSubmissionEvent(DesSubmissionAuditEventDetail(authority.get.ids.externalId, authProviderId, regId, desSubmissionState, jsSubmission))
      auditConnector.sendEvent(event)
    }
  }

  private[services] def auditDESTopUp(regId: String, jsSubmission: JsObject)(implicit hc: HeaderCarrier) = {
    val event = new DesTopUpEvent(DesTopUpAuditEventDetail(regId, jsSubmission))
    auditConnector.sendEvent(event)
  }

  private[services] class FailedToGetCredId extends NoStackTrace
  private[services] def retrieveCredId(implicit hc: HeaderCarrier): Future[String] = {
    authConnector.getCurrentAuthority flatMap {
      case Some(a) => Future.successful(a.gatewayId)
      case _ => Future.failed(new FailedToGetCredId)
    }
  }

  private[services] class FailedToGetLanguage extends NoStackTrace
  private[services] def retrieveLanguage(regId: String)(implicit hc: HeaderCarrier): Future[String] = {
    businessRegistrationConnector.retrieveCurrentProfile flatMap {
      case businessProfile if businessProfile.registrationID == regId => Future.successful(businessProfile.language)
      case _ => Future.failed(new FailedToGetLanguage)
    }
  }

  private[services] class SessionIDNotExists extends NoStackTrace
  private[services] def retrieveSessionID(hc: HeaderCarrier): String = {
    val s = hc.headers.collect{case ("X-Session-ID", x) => x}
    if( s.nonEmpty ) s.head else throw new SessionIDNotExists
  }
}
