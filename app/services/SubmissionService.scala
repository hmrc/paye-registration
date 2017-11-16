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

import common.exceptions.DBExceptions.MissingRegDocument
import common.exceptions.RegistrationExceptions._
import common.exceptions.SubmissionExceptions._
import common.constants.ETMPStatusCodes
import connectors._
import enums.{IncorporationStatus, PAYEStatus}
import models._
import models.incorporation.IncorpStatusUpdate
import models.submission._
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContent, Request}
import repositories._

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.util.{Failure, Success, Try}
import scala.util.control.NoStackTrace
import uk.gov.hmrc.http.HeaderCarrier

class RejectedIncorporationException(msg: String) extends NoStackTrace {
  override def getMessage: String = msg
}

@Singleton
class SubmissionService @Inject()(injSequenceMongoRepository: SequenceMongo,
                                  injRegistrationMongoRepository: RegistrationMongo,
                                  injDESConnector: DESConnector,
                                  injIncorporationInformationConnector: IncorporationInformationConnector,
                                  injAuthConnector: AuthConnector,
                                  injBusinessRegistrationConnector: BusinessRegistrationConnector,
                                  injCompanyRegistrationConnector: CompanyRegistrationConnector,
                                  injAuditService: AuditService) extends SubmissionSrv {
  val sequenceRepository = injSequenceMongoRepository.store
  val registrationRepository = injRegistrationMongoRepository.store
  val desConnector = injDESConnector
  val incorporationInformationConnector = injIncorporationInformationConnector
  val authConnector = injAuthConnector
  val businessRegistrationConnector = injBusinessRegistrationConnector
  val companyRegistrationConnector = injCompanyRegistrationConnector
  val auditService = injAuditService
}

trait SubmissionSrv extends ETMPStatusCodes {

  val sequenceRepository: SequenceRepository
  val registrationRepository: RegistrationRepository
  val desConnector: DESConnect
  val incorporationInformationConnector: IncorporationInformationConnect
  val authConnector: AuthConnect
  val businessRegistrationConnector: BusinessRegistrationConnect
  val companyRegistrationConnector: CompanyRegistrationConnect
  val auditService: AuditSrv

  private val REGIME = "paye"
  private val SUBSCRIBER = "SCRS"

  def submitToDes(regId: String)(implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[String] = {
    for {
      ackRef        <- assertOrGenerateAcknowledgementReference(regId)
      incUpdate     <- getIncorporationUpdate(regId) recover {
                          case ex: RejectedIncorporationException =>
                            updatePAYERegistrationDocument(regId, PAYEStatus.cancelled)
                            throw ex
                        }
      ctutr         <- incUpdate.fold[Future[Option[String]]](Future.successful(None))(_ => fetchCtUtr(regId, incUpdate))
      submission    <- buildADesSubmission(regId, incUpdate, ctutr)
      _             <- desConnector.submitToDES(submission, regId, incUpdate)
      _             <- auditService.auditDESSubmission(regId, incUpdate.fold("partial")(_ => "full"), Json.toJson[DESSubmission](submission).as[JsObject], ctutr)
      updatedStatus =  incUpdate.fold(PAYEStatus.held)(_ => PAYEStatus.submitted)
      _             <- updatePAYERegistrationDocument(regId, updatedStatus)
    } yield ackRef
  }


  def submitTopUpToDES(regId: String, incorpStatusUpdate: IncorpStatusUpdate)(implicit hc: HeaderCarrier): Future[PAYEStatus.Value] = {
    for {
      desSubmission <- buildTopUpDESSubmission(regId, incorpStatusUpdate)
      _             <- desConnector.submitTopUpToDES(desSubmission, regId, incorpStatusUpdate.transactionId)
      _             <- auditService.auditDESTopUp(regId, desSubmission)
      updatedStatus = if(incorpStatusUpdate.status == IncorporationStatus.rejected) PAYEStatus.cancelled else PAYEStatus.submitted
      status        <- updatePAYERegistrationDocument(regId, updatedStatus)
    } yield status
  }

  def getIncorporationUpdate(regId: String)(implicit hc: HeaderCarrier): Future[Option[IncorpStatusUpdate]] = {
    for {
      txId      <- registrationRepository.retrieveTransactionId(regId)
      incStatus <- incorporationInformationConnector.getIncorporationUpdate(txId, REGIME, SUBSCRIBER, regId) map {
        case Some(update) if update.status == IncorporationStatus.rejected => throw new RejectedIncorporationException(s"incorporation for regId $regId has been rejected")
        case response => response
      }
    } yield incStatus
  }

  private[services] def assertOrGenerateAcknowledgementReference(regId: String)(implicit ec: ExecutionContext): Future[String]= {
    registrationRepository.retrieveAcknowledgementReference(regId) flatMap {
      case Some(ackRef) => Future.successful(ackRef)
      case None => for {
        newAckref <- generateAcknowledgementReference
        _ <- registrationRepository.saveAcknowledgementReference(regId, newAckref)
      } yield newAckref
    }
  }

  private[services] def generateAcknowledgementReference(implicit ec: ExecutionContext): Future[String] = {
    val sequenceID = "AcknowledgementID"
    sequenceRepository.getNext(sequenceID)
      .map(ref => f"BRPY$ref%011d")
  }

  private[services] def buildADesSubmission(regId: String, incorpStatusUpdate: Option[IncorpStatusUpdate], ctutr: Option[String])(implicit hc: HeaderCarrier): Future[DESSubmission] = {
    registrationRepository.retrieveRegistration(regId) flatMap {
      case Some(payeReg) if payeReg.status == PAYEStatus.draft => incorpStatusUpdate match {
        case Some(statusUpdate) =>
          Logger.info("[SubmissionService] - [buildPartialOrFullDesSubmission]: building a full DES submission")
          payeReg2DESSubmission(payeReg, statusUpdate.crn, ctutr)
        case None =>
          Logger.info("[SubmissionService] - [buildPartialOrFullDesSubmission]: building a partial DES submission")
          payeReg2DESSubmission(payeReg, None, ctutr)
      }
      case Some(payeReg) =>
        Logger.warn(s"[SubmissionService] - [buildPartialDesSubmission]: The registration for regId $regId has incorrect status of ${payeReg.status.toString}s")
        throw new RegistrationInvalidStatus(regId, payeReg.status.toString)
      case None =>
        Logger.warn(s"[SubmissionService] - [buildPartialDesSubmission]:  building des top submission failed, there was no registration document present for regId $regId")
        throw new MissingRegDocument(regId)
    }
  }

  private[services] def buildTopUpDESSubmission(regId: String, incorpStatusUpdate: IncorpStatusUpdate)(implicit ec: ExecutionContext): Future[TopUpDESSubmission] = {
    registrationRepository.retrieveRegistration(regId) map {
      case Some(payeReg) if payeReg.status == PAYEStatus.held => payeReg2TopUpDESSubmission(payeReg, incorpStatusUpdate)
      case Some(payeReg) if List(PAYEStatus.draft, PAYEStatus.invalid).contains(payeReg.status) =>
        Logger.warn(s"[SubmissionService] - [buildTopUpDESSubmission]: paye status is currently ${payeReg.status} for registrationId $regId")
        throw new RegistrationInvalidStatus(regId, payeReg.status.toString)
      case Some(payeReg) =>
        Logger.error(s"[SubmissionService] - [buildTopUpDESSubmission]: paye status is currently ${payeReg.status} for registrationId $regId")
        throw new ErrorRegistrationException(regId, payeReg.status.toString)
      case None =>
        Logger.error(s"[SubmissionService] - [buildTopUpDESSubmission]: building des top submission failed, there was no registration document present for regId $regId")
        throw new MissingRegDocument(regId)
    }
  }

  private def updatePAYERegistrationDocument(regId: String, newStatus: PAYEStatus.Value)(implicit ec: ExecutionContext): Future[PAYEStatus.Value] = {
    registrationRepository.updateRegistrationStatus(regId, newStatus) map {
      _ => if(!newStatus.equals(PAYEStatus.cancelled)) {registrationRepository.cleardownRegistration(regId)}
        newStatus
    }
  }

  private[services] def payeReg2DESSubmission(payeReg: PAYERegistration, incorpUpdateCrn: Option[String], ctutr: Option[String])(implicit hc: HeaderCarrier): Future[DESSubmission] = {
    val companyDetails = payeReg.companyDetails.getOrElse {
      throw new CompanyDetailsNotDefinedException("Company Details not defined")
    }

    val ackRef = payeReg.acknowledgementReference.getOrElse {
      Logger.warn(s"[SubmissionService] - [payeReg2PartialDESSubmission]: Unable to convert to Partial DES Submission model for reg ID ${payeReg.registrationID}, Error: Missing Acknowledgement Ref")
      throw new AcknowledgementReferenceNotExistsException(payeReg.registrationID)
    }

    buildDESMetaData(payeReg.registrationID,payeReg.formCreationTimestamp, payeReg.completionCapacity) map {
      desMetaData => {
        DESSubmission(
          acknowledgementReference = ackRef,
          metaData = desMetaData,
          limitedCompany = buildDESLimitedCompany(companyDetails, payeReg.sicCodes, incorpUpdateCrn, payeReg.directors, payeReg.employment, ctutr),
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
      case s if s == Seq.empty => throw new SICCodeNotDefinedException("No SIC Codes provided")
      case s => s.head.description.getOrElse(throw new SICCodeNotDefinedException("Empty description in first SIC Code"))
    }
  }

  private[services] def buildDESMetaData(regId: String, timestamp:String, completionCapacity: Option[String])(implicit hc: HeaderCarrier): Future[DESMetaData] = {
    for {
      language <- retrieveLanguage(regId)
      credId <- retrieveCredId
      sessionId = retrieveSessionID(hc)
    } yield {
      DESMetaData(sessionId, credId, language, timestamp, DESCompletionCapacity.buildDESCompletionCapacity(completionCapacity))
    }
  }

  private[services] def buildDESLimitedCompany(companyDetails: CompanyDetails,
                                               sicCodes: Seq[SICCode],
                                               incorpUpdateCrn: Option[String],
                                               directors: Seq[Director],
                                               employment: Option[Employment],
                                               ctutr: Option[String]): DESLimitedCompany = {
    val EmploymentDetails = employment.getOrElse(throw new EmploymentDetailsNotDefinedException("Employment details not defined"))
    if(directors.isEmpty) throw new DirectorsNotCompletedException("No director details provided") else {
      DESLimitedCompany(
        companyUTR = ctutr,
        companiesHouseCompanyName = companyDetails.companyName,
        nameOfBusiness = companyDetails.tradingName,
        businessAddress = companyDetails.ppobAddress,
        businessContactDetails = companyDetails.businessContactDetails,
        natureOfBusiness = buildNatureOfBusiness(sicCodes),
        crn = incorpUpdateCrn,
        directors = directors,
        registeredOfficeAddress = companyDetails.roAddress,
        operatingOccPensionScheme = EmploymentDetails.companyPension
      )
    }
  }

  private[services] def buildDESEmployingPeople(regId: String, employment: Option[Employment], payeContact: Option[PAYEContact]): DESEmployingPeople = {
    val employmentDetails = employment.getOrElse(throw new EmploymentDetailsNotDefinedException("Employment details not defined"))
    val payeContactDetails = payeContact.getOrElse(throw new PAYEContactNotDefinedException("PAYE Contact not defined"))

    DESEmployingPeople(
      dateOfFirstEXBForEmployees = employmentDetails.firstPaymentDate,
      numberOfEmployeesExpectedThisYear = if( employmentDetails.employees ) "1" else "0",
      engageSubcontractors = employmentDetails.subcontractors,
      correspondenceName = payeContactDetails.contactDetails.name,
      correspondenceContactDetails = payeContactDetails.contactDetails.digitalContactDetails,
      payeCorrespondenceAddress = payeContactDetails.correspondenceAddress
    )
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
    businessRegistrationConnector.retrieveCurrentProfile(regId) flatMap {
      case businessProfile if businessProfile.registrationID == regId => Future.successful(businessProfile.language)
      case _ => Future.failed(new FailedToGetLanguage)
    }
  }

  private[services] class SessionIDNotExists extends NoStackTrace
  private[services] def retrieveSessionID(hc: HeaderCarrier): String = {
    val s = hc.headers.collect{case ("X-Session-ID", x) => x}
    if( s.nonEmpty ) s.head else throw new SessionIDNotExists
  }

  private[services] def fetchCtUtr(regId: String, incorpUpdate: Option[IncorpStatusUpdate])(implicit hc: HeaderCarrier): Future[Option[String]] = {
    val txId = incorpUpdate.map(_.transactionId)
    companyRegistrationConnector.fetchCompanyRegistrationDocument(regId, txId) map { response =>
      Try((response.json \ "acknowledgementReferences" \  "ctUtr").as[String]) match {
        case Success(ctutr) => Some(ctutr)
        case Failure(_)     => None
      }
    }
  }
}
