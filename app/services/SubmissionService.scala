/*
 * Copyright 2024 HM Revenue & Customs
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

import common.constants.ETMPStatusCodes
import common.exceptions.DBExceptions.MissingRegDocument
import common.exceptions.RegistrationExceptions._
import common.exceptions.SubmissionExceptions._
import connectors._
import enums.{Employing, IncorporationStatus, PAYEStatus}

import javax.inject.{Inject, Singleton}
import models._
import models.incorporation.IncorpStatusUpdate
import models.submission.{DESMetaData, _}
import utils.Logging
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContent, Request}
import repositories._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException, AuthorisedFunctions, NoActiveSession}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace


class RejectedIncorporationException(msg: String) extends NoStackTrace {
  override def getMessage: String = msg
}

@Singleton
class SubmissionService @Inject()(sequenceMongoRepository: SequenceMongoRepository,
                                  registrationMongoRepository: RegistrationMongoRepository,
                                  routingConnector: RoutingConnector,
                                  incorporationInformationConnector: IncorporationInformationConnector,
                                  businessRegistrationConnector: BusinessRegistrationConnector,
                                  companyRegistrationConnector: CompanyRegistrationConnector,
                                  auditService: AuditService,
                                  registrationService: RegistrationService,
                                  val authConnector: AuthConnector)(implicit ec: ExecutionContext) extends ETMPStatusCodes with AuthorisedFunctions with Logging {

  private val REGIME = "paye"
  private val SUBSCRIBER = "SCRS"

  def submitToEtmp(regId: String)(implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[String] = {
    val futureAckRefIncUpdate = {
      for {
        ackRef <- assertOrGenerateAcknowledgementReference(regId)
        incUpdate <- getIncorporationUpdate(regId)
      } yield {
        (ackRef, incUpdate)
      }
    }.recoverWith {
      case ex: RejectedIncorporationException =>
        registrationService.deletePAYERegistration(regId, PAYEStatus.draft).map(_ => throw ex)
    }

    futureAckRefIncUpdate.flatMap { ackRefAndIncUpdate =>
      val (ackRef, incUpdate) = ackRefAndIncUpdate
      for {
        ctutr <- incUpdate.fold[Future[Option[String]]](Future.successful(None))(_ => fetchCtUtr(regId, incUpdate))
        submission <- buildEtmpSubmission(regId, incUpdate, ctutr)
        _ <- routingConnector.submitRegistration(submission, regId, incUpdate)
        _ <- auditService.auditEtmpSubmission(regId, incUpdate.fold("partial")(_ => "full"), Json.toJson[EtmpSubmission](submission).as[JsObject], ctutr)
        updatedStatus = incUpdate.fold(PAYEStatus.held)(_ => PAYEStatus.submitted)
        _ <- updatePAYERegistrationDocument(regId, updatedStatus)
      } yield ackRef
    }
  }


  def submitTopUpToEtmp(regId: String, incorpStatusUpdate: IncorpStatusUpdate)(implicit hc: HeaderCarrier): Future[PAYEStatus.Value] = {
    for {
      apiSubmission <- buildTopUpEtmpSubmission(regId, incorpStatusUpdate)
      _ <- routingConnector.submitIncorporation(apiSubmission, regId, incorpStatusUpdate.transactionId)
      _ <- auditService.auditEtmpTopUpSubmission(regId, apiSubmission)
      _ <- if (incorpStatusUpdate.status == IncorporationStatus.rejected) {
        registrationService.deletePAYERegistration(regId, PAYEStatus.held)
      } else {
        updatePAYERegistrationDocument(regId, PAYEStatus.submitted)
      }
    } yield incorpStatusUpdate.status match {
      case IncorporationStatus.rejected => PAYEStatus.cancelled
      case _ => PAYEStatus.submitted
    }
  }

  def getIncorporationUpdate(regId: String)(implicit hc: HeaderCarrier): Future[Option[IncorpStatusUpdate]] = {
    for {
      txId <- registrationMongoRepository.retrieveTransactionId(regId)
      incStatus <- incorporationInformationConnector.getIncorporationUpdate(txId, REGIME, SUBSCRIBER, regId)
    } yield {
      incStatus match {
        case Some(update) if update.status == IncorporationStatus.rejected => throw new RejectedIncorporationException(s"incorporation for regId $regId has been rejected")
        case response => response
      }
    }
  }

  private[services] def assertOrGenerateAcknowledgementReference(regId: String): Future[String] = {
    registrationMongoRepository.retrieveAcknowledgementReference(regId) flatMap {
      case Some(ackRef) => Future.successful(ackRef)
      case None => for {
        newAckref <- generateAcknowledgementReference
        _ <- registrationMongoRepository.saveAcknowledgementReference(regId, newAckref)
      } yield newAckref
    }
  }

  private[services] def generateAcknowledgementReference: Future[String] = {
    val sequenceID = "AcknowledgementID"
    sequenceMongoRepository.getNext(sequenceID)
      .map(ref => f"BRPY$ref%011d")
  }

  private[services] def buildEtmpSubmission(regId: String, incorpStatusUpdate: Option[IncorpStatusUpdate], ctutr: Option[String])(implicit hc: HeaderCarrier): Future[EtmpSubmission] = {
    registrationMongoRepository.retrieveRegistration(regId) flatMap {
      case Some(payeReg) if payeReg.status == PAYEStatus.draft => incorpStatusUpdate match {
        case Some(statusUpdate) =>
          logger.debug("[buildEtmpSubmission] building a full ETMP submission")
          payeReg2ETMPSubmission(payeReg, statusUpdate.crn, ctutr)
        case None =>
          logger.debug("[buildEtmpSubmission] building a partial ETMP submission")
          payeReg2ETMPSubmission(payeReg, None, ctutr)
      }
      case Some(payeReg) =>
        logger.warn(s"[buildEtmpSubmission] The registration for regId $regId has incorrect status of ${payeReg.status.toString}s")
        throw new RegistrationInvalidStatus(regId, payeReg.status.toString)
      case None =>
        logger.warn(s"[buildEtmpSubmission] building ETMP top submission failed, there was no registration document present for regId $regId")
        throw new MissingRegDocument(regId)
    }
  }

  private[services] def buildTopUpEtmpSubmission(regId: String, incorpStatusUpdate: IncorpStatusUpdate): Future[TopUpEtmpSubmission] = {
    registrationMongoRepository.retrieveRegistration(regId) map {
      case Some(payeReg) if payeReg.status == PAYEStatus.held => payeReg2TopUpETMPSubmission(payeReg, incorpStatusUpdate)
      case Some(payeReg) if List(PAYEStatus.draft, PAYEStatus.invalid).contains(payeReg.status) =>
        logger.warn(s"[buildTopUpEtmpSubmission] paye status is currently ${payeReg.status} for registrationId $regId")
        throw new RegistrationInvalidStatus(regId, payeReg.status.toString)
      case Some(payeReg) =>
        logger.error(s"[buildTopUpEtmpSubmission] paye status is currently ${payeReg.status} for registrationId $regId")
        throw new ErrorRegistrationException(regId, payeReg.status.toString)
      case None =>
        logger.error(s"[buildTopUpEtmpSubmission] building ETMP top submission failed, there was no registration document present for regId $regId")
        throw new MissingRegDocument(regId)
    }
  }

  private def updatePAYERegistrationDocument(regId: String, newStatus: PAYEStatus.Value): Future[PAYEStatus.Value] = {
    registrationMongoRepository.updateRegistrationStatus(regId, newStatus) flatMap {
      _ =>
        if (!newStatus.equals(PAYEStatus.cancelled)) {
          registrationMongoRepository.cleardownRegistration(regId).map { _ =>
            newStatus
          }
        } else {
          Future.successful(newStatus)
        }
    }
  }

  private[services] def payeReg2ETMPSubmission(payeReg: PAYERegistration, incorpUpdateCrn: Option[String], ctutr: Option[String])(implicit hc: HeaderCarrier): Future[EtmpSubmission] = {
    val companyDetails = payeReg.companyDetails.getOrElse {
      throw new CompanyDetailsNotDefinedException("Company Details not defined")
    }

    val ackRef = payeReg.acknowledgementReference.getOrElse {
      logger.warn(s"[payeReg2PartialETMPSubmission] Unable to convert to Partial ETMP Submission model for reg ID ${payeReg.registrationID}, Error: Missing Acknowledgement Ref")
      throw new AcknowledgementReferenceNotExistsException(payeReg.registrationID)
    }

    val employmentInfo = payeReg.employmentInfo.getOrElse {
      throw new EmploymentDetailsNotDefinedException("Employment Info not defined")
    }

    buildDESMetaData(payeReg.registrationID, payeReg.formCreationTimestamp, payeReg.completionCapacity) map {
      apiMetaData => {
        EtmpSubmission(
          acknowledgementReference = ackRef,
          metaData = apiMetaData,
          limitedCompany = buildDESLimitedCompany(companyDetails, payeReg.sicCodes, incorpUpdateCrn, payeReg.directors, employmentInfo, ctutr),
          employingPeople = buildDESEmployingPeople(
            payeReg.registrationID,
            employmentInfo,
            payeReg.payeContact)
        )
      }
    }
  }

  private[services] def payeReg2TopUpETMPSubmission(payeReg: PAYERegistration, incorpStatusUpdate: IncorpStatusUpdate): TopUpEtmpSubmission = {
    TopUpEtmpSubmission(
      acknowledgementReference = payeReg.acknowledgementReference.getOrElse {
        logger.warn(s"[payeReg2TopUpETMPSubmission] Unable to convert to Top Up ETMP Submission model for reg ID ${payeReg.registrationID}, Error: Missing Acknowledgement Ref")
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

  private[services] def buildDESMetaData(regId: String, timestamp: String, completionCapacity: Option[String])(implicit hc: HeaderCarrier): Future[DESMetaData] = {
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
                                               employment: EmploymentInfo,
                                               ctutr: Option[String]): DESLimitedCompany = {
    if (directors.isEmpty) throw new DirectorsNotCompletedException("No director details provided") else {
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
        operatingOccPensionScheme = employment.companyPension
      )
    }
  }

  private def buildDesEmploymentInfo(employmentDetails: EmploymentInfo, payeContactDetails: PAYEContact): DESEmployingPeople = {
    DESEmployingPeople(
      dateOfFirstEXBForEmployees = employmentDetails.firstPaymentDate,
      numberOfEmployeesExpectedThisYear = if (employmentDetails.employees != Employing.notEmploying) "1" else "0",
      engageSubcontractors = employmentDetails.subcontractors,
      correspondenceName = payeContactDetails.contactDetails.name,
      correspondenceContactDetails = payeContactDetails.contactDetails.digitalContactDetails,
      payeCorrespondenceAddress = payeContactDetails.correspondenceAddress
    )
  }

  private[services] def buildDESEmployingPeople(regId: String, employment: EmploymentInfo, payeContact: Option[PAYEContact]): DESEmployingPeople = {
    val payeContactDetails = payeContact.getOrElse(throw new PAYEContactNotDefinedException("PAYE Contact not defined"))
    buildDesEmploymentInfo(employment, payeContactDetails)
  }

  private[services] class FailedToGetCredId extends NoStackTrace

  private[services] def retrieveCredId()(implicit hc: HeaderCarrier): Future[String] = {
    authorised().retrieve(Retrievals.credentials) {
      case Some(credentials) =>
        Future.successful(credentials.providerId)
      case None =>
        throw new FailedToGetCredId
    } recoverWith {
      case ex: NoActiveSession =>
        logger.warn(s"[retrieveCredId] User was not logged in, No Active Session. Reason: '${ex.reason}'")
        throw ex
      case ex: AuthorisationException =>
        logger.warn(s"[retrieveCredId] User has an Active Session but is not authorised. Reason: '${ex.reason}'")
        throw ex
      case ex =>
        logger.error(s"[retrieveCredId] Unexpected Exception thrown when calling Auth. Exception: '$ex'")
        throw ex
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
    hc.sessionId match {
      case Some(sesId) => sesId.value
      case _ => throw new SessionIDNotExists
    }
  }

  private[services] def fetchCtUtr(regId: String, incorpUpdate: Option[IncorpStatusUpdate])(implicit hc: HeaderCarrier): Future[Option[String]] = {
    val txId = incorpUpdate.map(_.transactionId)
    companyRegistrationConnector.fetchCtUtr(regId, txId)
  }
}
