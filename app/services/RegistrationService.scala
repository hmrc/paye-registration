/*
 * Copyright 2022 HM Revenue & Customs
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

import common.exceptions.DBExceptions.MissingRegDocument
import common.exceptions.RegistrationExceptions.{RegistrationFormatException, UnmatchedStatusException}
import config.AppConfig
import connectors.IncorporationInformationConnector
import enums.{Employing, PAYEStatus}
import helpers.PAYEBaseValidator
import models._
import utils.Logging
import play.api.libs.json.{JsObject, Json}
import repositories.RegistrationMongoRepository
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegistrationService @Inject()(val registrationRepository: RegistrationMongoRepository,
                                    auditService: AuditService,
                                    incorporationInformationConnector: IncorporationInformationConnector,
                                    appConfig: AppConfig)(implicit ec: ExecutionContext) extends PAYEBaseValidator with Logging {

  def createNewPAYERegistration(regID: String, transactionID: String, internalId: String): Future[PAYERegistration] = {
    registrationRepository.retrieveRegistration(regID) flatMap {
      case None => registrationRepository.createNewRegistration(regID, transactionID, internalId)
      case Some(registration) =>
        logger.info(s"[createNewPAYERegistration] Cannot create new registration for reg ID '$regID' as registration already exists")
        Future.successful(registration)
    }
  }

  def fetchPAYERegistration(regID: String): Future[Option[PAYERegistration]] = {
    registrationRepository.retrieveRegistration(regID)
  }

  def fetchPAYERegistrationByTransactionID(transactionID: String): Future[Option[PAYERegistration]] = {
    registrationRepository.retrieveRegistrationByTransactionID(transactionID)
  }

  def getRegistrationId(txId: String): Future[String] = {
    registrationRepository.getRegistrationId(txId)
  }

  def getCompanyDetails(regID: String): Future[Option[CompanyDetails]] = {
    registrationRepository.retrieveCompanyDetails(regID)
  }

  def upsertCompanyDetails(regID: String, companyDetails: CompanyDetails): Future[CompanyDetails] = {
    if (validDigitalContactDetails(companyDetails.businessContactDetails)) registrationRepository.upsertCompanyDetails(regID, companyDetails)
    else throw new RegistrationFormatException(s"No business contact method submitted for regID $regID")
  }

  def getEmploymentInfo(regID: String): Future[Option[EmploymentInfo]] = {
    registrationRepository.retrieveEmploymentInfo(regID)
  }

  def upsertEmploymentInfo(regID: String, employmentDetails: EmploymentInfo): Future[EmploymentInfo] = {
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
      txId <- registrationRepository.retrieveTransactionId(regId)
      incorpDate <- incorporationInformationConnector.getIncorporationDate(txId)
    } yield incorpDate
  }

  def getDirectors(regID: String): Future[Seq[Director]] = {
    registrationRepository.retrieveDirectors(regID)
  }

  def upsertDirectors(regID: String, directors: Seq[Director]): Future[Seq[Director]] = {
    if (directors.exists(_.nino.isDefined)) registrationRepository.upsertDirectors(regID, directors)
    else throw new RegistrationFormatException(s"No director NINOs completed for reg ID $regID")
  }

  def getSICCodes(regID: String): Future[Seq[SICCode]] = {
    registrationRepository.retrieveSICCodes(regID)
  }

  def upsertSICCodes(regID: String, sicCodes: Seq[SICCode]): Future[Seq[SICCode]] = {
    registrationRepository.upsertSICCodes(regID, sicCodes)
  }

  def getPAYEContact(regID: String): Future[Option[PAYEContact]] = {
    registrationRepository.retrievePAYEContact(regID)
  }

  def upsertPAYEContact(regID: String, payeContact: PAYEContact): Future[PAYEContact] = {
    if (validPAYEContact(payeContact)) registrationRepository.upsertPAYEContact(regID, payeContact)
    else throw new RegistrationFormatException(s"No PAYE contact method submitted for regID $regID")
  }

  def getCompletionCapacity(regID: String): Future[Option[String]] = {
    registrationRepository.retrieveCompletionCapacity(regID)
  }

  def upsertCompletionCapacity(regID: String, capacity: String)(implicit hc: HeaderCarrier): Future[String] = {
    registrationRepository.retrieveRegistration(regID) flatMap {
      case Some(reg) =>
        if (reg.completionCapacity.nonEmpty && !reg.completionCapacity.get.equals(capacity)) {
          auditService.auditCompletionCapacity(regID, reg.completionCapacity.get, capacity)
        }
        registrationRepository.upsertCompletionCapacity(regID, capacity)
      case None =>
        logger.warn(s"[upsertCompletionCapacity] Unable to update Completion Capacity for reg ID $regID, Error: Couldn't retrieve an existing registration with that ID")
        throw new MissingRegDocument(regID)
    }
  }

  def getAcknowledgementReference(regID: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
    registrationRepository.retrieveAcknowledgementReference(regID)
  }

  def getStatus(regID: String)(implicit ec: ExecutionContext): Future[JsObject] = {
    registrationRepository.retrieveRegistration(regID) flatMap {
      case Some(registration) => {
        val lastUpdate = registration.status match {
          case PAYEStatus.held => registration.partialSubmissionTimestamp
          case PAYEStatus.submitted => registration.fullSubmissionTimestamp
          case PAYEStatus.cancelled => Some(registration.lastUpdate)
          case _@(PAYEStatus.acknowledged | PAYEStatus.rejected) => registration.acknowledgedTimestamp
          case _ => Some(registration.formCreationTimestamp)
        }

        val json = Json.obj("status" -> registration.status,
          "lastUpdate" -> lastUpdate.get)
        val ackRef = registration.acknowledgementReference.fold(Json.obj())(ackRef => Json.obj("ackRef" -> ackRef))
        val empRef = json ++ registration.registrationConfirmation.fold(Json.obj()) { empRefNotif =>
          empRefNotif.empRef.fold(Json.obj())(empRef => Json.obj("empref" -> empRef))
        }
        val restartURL = if (registration.status.equals(PAYEStatus.rejected)) Json.obj("restartURL" -> appConfig.payeRestartURL) else Json.obj()
        val cancelURL = if (Seq(PAYEStatus.draft, PAYEStatus.invalid).contains(registration.status)) Json.obj("cancelURL" -> appConfig.payeCancelURL.replace(":regID", regID)) else Json.obj()

        Future.successful(json ++ ackRef ++ empRef ++ restartURL ++ cancelURL)
      }
      case None => {
        logger.warn(s"[RegistrationService] [getStatus] No PAYE registration document found for registration ID $regID")
        throw new MissingRegDocument(regID)
      }
    }
  }

  def deletePAYERegistration(regID: String, validStatuses: PAYEStatus.Value*)(implicit ec: ExecutionContext): Future[Boolean] = {
    registrationRepository.retrieveRegistration(regID) flatMap {
      case Some(document) => document.status match {
        case documentStatus if validStatuses.contains(documentStatus) => registrationRepository.deleteRegistration(regID)
        case _ =>
          logger.warn(s"[RegistrationService] - [deletePAYERegistration] PAYE Reg document for regId $regID was not deleted as the document status was ${document.status}, not ${validStatuses.toString}")
          throw new UnmatchedStatusException
      }
      case None => throw new MissingRegDocument(regID)
    }
  }
}
