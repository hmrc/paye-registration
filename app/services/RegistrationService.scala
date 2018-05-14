/*
 * Copyright 2018 HM Revenue & Customs
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

import java.time.LocalDate

import common.exceptions.DBExceptions.MissingRegDocument
import common.exceptions.RegistrationExceptions.{RegistrationFormatException, UnmatchedStatusException}
import connectors.{IncorporationInformationConnect, IncorporationInformationConnector}
import enums.{Employing, PAYEStatus}
import helpers.PAYEBaseValidator
import javax.inject.{Inject, Singleton}
import models._
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import repositories.{RegistrationMongo, RegistrationMongoRepository, RegistrationRepository}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegistrationService @Inject()(injRegistrationMongoRepository: RegistrationMongo,
                                    injAuditService: AuditService,
                                    injIncorporationInformationConnector: IncorporationInformationConnector) extends RegistrationSrv with ServicesConfig {
  val registrationRepository : RegistrationMongoRepository = injRegistrationMongoRepository.store
  lazy val payeRestartURL = getString("api.payeRestartURL")
  lazy val payeCancelURL = getString("api.payeCancelURL")
  val auditService = injAuditService
  val incorporationInformationConnector = injIncorporationInformationConnector
}

trait RegistrationSrv extends PAYEBaseValidator {

  val registrationRepository : RegistrationRepository
  val payeRestartURL : String
  val payeCancelURL : String
  val auditService: AuditSrv
  val incorporationInformationConnector: IncorporationInformationConnect

  def createNewPAYERegistration(regID: String, transactionID: String, internalId : String)(implicit ec: ExecutionContext): Future[PAYERegistration] = {
    registrationRepository.retrieveRegistration(regID) flatMap {
      case None => registrationRepository.createNewRegistration(regID, transactionID, internalId)
      case Some(registration) =>
        Logger.info(s"Cannot create new registration for reg ID '$regID' as registration already exists")
        Future.successful(registration)
    }
  }

  def fetchPAYERegistration(regID: String)(implicit ec: ExecutionContext): Future[Option[PAYERegistration]] = {
    registrationRepository.retrieveRegistration(regID)
  }

  def fetchPAYERegistrationByTransactionID(transactionID: String)(implicit ec: ExecutionContext): Future[Option[PAYERegistration]] = {
    registrationRepository.retrieveRegistrationByTransactionID(transactionID)
  }

  def getCompanyDetails(regID: String)(implicit ec: ExecutionContext): Future[Option[CompanyDetails]] = {
    registrationRepository.retrieveCompanyDetails(regID)
  }

  def upsertCompanyDetails(regID: String, companyDetails: CompanyDetails)(implicit ec: ExecutionContext): Future[CompanyDetails] = {
    if(validDigitalContactDetails(companyDetails.businessContactDetails)) registrationRepository.upsertCompanyDetails(regID, companyDetails)
    else throw new RegistrationFormatException(s"No business contact method submitted for regID $regID")
  }

  def getEmploymentInfo(regID: String)(implicit ec: ExecutionContext): Future[Option[EmploymentInfo]] = {
    registrationRepository.retrieveEmploymentInfo(regID)
  }

  def upsertEmploymentInfo(regID: String, employmentDetails: EmploymentInfo)(implicit ec: ExecutionContext): Future[EmploymentInfo] = {
    registrationRepository.upsertEmploymentInfo(regID, employmentDetails) flatMap { res =>
      val status = (res.employees, res.construction) match {
        case (Employing.notEmploying, false) => PAYEStatus.invalid
        case _ => PAYEStatus.draft
      }

      registrationRepository.updateRegistrationStatus(regID, status) map (_ => res)
    }
  }

  def getIncorporationDate(regId: String)(implicit hc: HeaderCarrier): Future[Option[LocalDate]] = {
    for {
      txId       <- registrationRepository.retrieveTransactionId(regId)
      incorpDate <- incorporationInformationConnector.getIncorporationDate(txId)
    } yield incorpDate
  }

  @deprecated("use getEmploymentInfo instead for the new model",  "SCRS-11281")
  def getEmployment(regID: String)(implicit ec: ExecutionContext): Future[Option[Employment]] = {
    registrationRepository.retrieveEmployment(regID)
  }

  @deprecated("use upsertEmploymentInfo instead for the new model",  "SCRS-11281")
  def upsertEmployment(regID: String, employmentDetails: Employment)(implicit ec: ExecutionContext): Future[Employment] = {
    registrationRepository.upsertEmployment(regID, employmentDetails) flatMap {
      result =>
        (employmentDetails.subcontractors, employmentDetails.employees) match {
          case (false, false) =>
            if(!result.status.equals(PAYEStatus.invalid)) {
                registrationRepository.updateRegistrationStatus(regID, PAYEStatus.invalid) map { _ => employmentDetails}
              }else {Future.successful(employmentDetails)}
          case _ =>
            if(!result.status.equals(PAYEStatus.draft)) {
                registrationRepository.updateRegistrationStatus(regID, PAYEStatus.draft) map {_ => employmentDetails}
            } else {Future.successful(employmentDetails)}
        }
    }
  }

  def getDirectors(regID: String)(implicit ec: ExecutionContext): Future[Seq[Director]] = {
    registrationRepository.retrieveDirectors(regID)
  }

  def upsertDirectors(regID: String, directors: Seq[Director])(implicit ec: ExecutionContext): Future[Seq[Director]] = {
    if(directors.exists(_.nino.isDefined)) registrationRepository.upsertDirectors(regID, directors)
    else throw new RegistrationFormatException(s"No director NINOs completed for reg ID $regID")
  }

  def getSICCodes(regID: String)(implicit ec: ExecutionContext): Future[Seq[SICCode]] = {
    registrationRepository.retrieveSICCodes(regID)
  }

  def upsertSICCodes(regID: String, sicCodes: Seq[SICCode])(implicit ec: ExecutionContext): Future[Seq[SICCode]] = {
    registrationRepository.upsertSICCodes(regID, sicCodes)
  }

  def getPAYEContact(regID: String)(implicit ec: ExecutionContext): Future[Option[PAYEContact]] = {
    registrationRepository.retrievePAYEContact(regID)
  }

  def upsertPAYEContact(regID: String, payeContact: PAYEContact)(implicit ec: ExecutionContext): Future[PAYEContact] = {
    if(validPAYEContact(payeContact)) registrationRepository.upsertPAYEContact(regID, payeContact)
    else throw new RegistrationFormatException(s"No PAYE contact method submitted for regID $regID")
  }

  def getCompletionCapacity(regID: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
    registrationRepository.retrieveCompletionCapacity(regID)
  }

  def upsertCompletionCapacity(regID: String, capacity: String)(implicit hc: HeaderCarrier): Future[String] = {
    registrationRepository.retrieveRegistration(regID) flatMap {
      case Some(reg) =>
        if( reg.completionCapacity.nonEmpty && !reg.completionCapacity.get.equals(capacity) ) {
          auditService.auditCompletionCapacity(regID, reg.completionCapacity.get, capacity)
        }
        registrationRepository.upsertCompletionCapacity(regID, capacity)
      case None =>
        Logger.warn(s"Unable to update Completion Capacity for reg ID $regID, Error: Couldn't retrieve an existing registration with that ID")
        throw new MissingRegDocument(regID)
    }
  }

  def getAcknowledgementReference(regID: String)(implicit ec: ExecutionContext) : Future[Option[String]] = {
    registrationRepository.retrieveAcknowledgementReference(regID)
  }

  def getEligibility(regID: String)(implicit ec: ExecutionContext): Future[Option[Eligibility]] = {
    registrationRepository.getEligibility(regID)
  }

  def updateEligibility(regID: String, eligibility: Eligibility)(implicit ec: ExecutionContext): Future[Eligibility] = {
    registrationRepository.upsertEligibility(regID, eligibility)
  }

  def getStatus(regID: String)(implicit ec: ExecutionContext): Future[JsObject] = {
    registrationRepository.retrieveRegistration(regID) flatMap {
      case Some(registration) => {
        val lastUpdate = registration.status match {
          case PAYEStatus.held => registration.partialSubmissionTimestamp
          case PAYEStatus.submitted => registration.fullSubmissionTimestamp
          case PAYEStatus.cancelled => Some(registration.lastUpdate)
          case _ @ (PAYEStatus.acknowledged | PAYEStatus.rejected) => registration.acknowledgedTimestamp
          case _ => Some(registration.formCreationTimestamp)
        }

        val json = Json.obj("status" -> registration.status,
                            "lastUpdate" -> lastUpdate.get)
        val ackRef = registration.acknowledgementReference.fold(Json.obj())(ackRef => Json.obj("ackRef" -> ackRef))
        val empRef = json ++ registration.registrationConfirmation.fold(Json.obj()) { empRefNotif =>
          empRefNotif.empRef.fold(Json.obj())(empRef => Json.obj("empref" -> empRef))
        }
        val restartURL = if(registration.status.equals(PAYEStatus.rejected)) Json.obj("restartURL" -> payeRestartURL) else Json.obj()
        val cancelURL = if(Seq(PAYEStatus.draft, PAYEStatus.invalid).contains(registration.status)) Json.obj("cancelURL" -> payeCancelURL.replace(":regID", regID)) else Json.obj()

        Future.successful(json ++ ackRef ++ empRef ++ restartURL ++ cancelURL)
      }
      case None => {
        Logger.warn(s"[RegistrationService] [getStatus] No PAYE registration document found for registration ID $regID")
        throw new MissingRegDocument(regID)
      }
    }
  }

  def deletePAYERegistration(regID: String, validStatuses: PAYEStatus.Value*)(implicit ec: ExecutionContext): Future[Boolean] = {
    registrationRepository.retrieveRegistration(regID) flatMap {
      case Some(document) => document.status match {
        case documentStatus if validStatuses.contains(documentStatus) => registrationRepository.deleteRegistration(regID)
        case _ =>
          Logger.warn(s"[RegistrationService] - [deletePAYERegistration] PAYE Reg document for regId $regID was not deleted as the document status was ${document.status}, not ${validStatuses.toString}")
          throw new UnmatchedStatusException
      }
      case None => throw new MissingRegDocument(regID)
    }
  }
}
