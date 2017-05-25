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

import enums.PAYEStatus
import helpers.PAYEBaseValidator
import models._
import repositories.{RegistrationMongo, RegistrationMongoRepository, RegistrationRepository}
import common.exceptions.RegistrationExceptions.RegistrationFormatException
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import common.exceptions.DBExceptions.MissingRegDocument

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class RegistrationService @Inject()(injRegistrationMongoRepository: RegistrationMongo) extends RegistrationSrv {
  val registrationRepository : RegistrationMongoRepository = injRegistrationMongoRepository.store
}

trait RegistrationSrv extends PAYEBaseValidator {

  val registrationRepository : RegistrationRepository

  def createNewPAYERegistration(regID: String, transactionID: String, internalId : String): Future[PAYERegistration] = {
    registrationRepository.retrieveRegistration(regID) flatMap {
      case None => registrationRepository.createNewRegistration(regID, transactionID, internalId)
      case Some(registration) =>
        Logger.info(s"Cannot create new registration for reg ID '$regID' as registration already exists")
        Future.successful(registration)
    }
  }

  def fetchPAYERegistration(regID: String): Future[Option[PAYERegistration]] = {
    registrationRepository.retrieveRegistration(regID)
  }

  def fetchPAYERegistrationByTransactionID(transactionID: String): Future[Option[PAYERegistration]] = {
    registrationRepository.retrieveRegistrationByTransactionID(transactionID)
  }

  def getCompanyDetails(regID: String): Future[Option[CompanyDetails]] = {
    registrationRepository.retrieveCompanyDetails(regID)
  }

  def upsertCompanyDetails(regID: String, companyDetails: CompanyDetails): Future[CompanyDetails] = {
    if(validDigitalContactDetails(companyDetails.businessContactDetails)) registrationRepository.upsertCompanyDetails(regID, companyDetails)
    else throw new RegistrationFormatException(s"No business contact method submitted for regID $regID")
  }

  def getEmployment(regID: String): Future[Option[Employment]] = {
    registrationRepository.retrieveEmployment(regID)
  }

  def upsertEmployment(regID: String, employmentDetails: Employment): Future[Employment] = {
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

  def getDirectors(regID: String): Future[Seq[Director]] = {
    registrationRepository.retrieveDirectors(regID)
  }

  def upsertDirectors(regID: String, directors: Seq[Director]): Future[Seq[Director]] = {
    if(directors.exists(_.nino.isDefined)) registrationRepository.upsertDirectors(regID, directors)
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
    if(validPAYEContact(payeContact)) registrationRepository.upsertPAYEContact(regID, payeContact)
    else throw new RegistrationFormatException(s"No PAYE contact method submitted for regID $regID")
  }

  def getCompletionCapacity(regID: String): Future[Option[String]] = {
    registrationRepository.retrieveCompletionCapacity(regID)
  }

  def upsertCompletionCapacity(regID: String, capacity: String): Future[String] = {
    if(validCompletionCapacity(capacity)) registrationRepository.upsertCompletionCapacity(regID, capacity)
    else throw new RegistrationFormatException(s"Invalid completion capacity submitted for reg ID $regID")
  }

  def getAcknowledgementReference(regID: String) : Future[Option[String]] = {
    registrationRepository.retrieveAcknowledgementReference(regID)
  }

  def getEligibility(regID: String): Future[Option[Eligibility]] = {
    registrationRepository.getEligibility(regID)
  }

  def updateEligibility(regID: String, eligibility: Eligibility): Future[Eligibility] = {
    registrationRepository.upsertEligibility(regID, eligibility)
  }

  def getStatus(regID: String): Future[JsObject] = {
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
        Future.successful(json ++ ackRef ++ empRef)
      }
      case None => {
        Logger.warn(s"[RegistrationService] [getStatus] No PAYE registration document found for registration ID $regID")
        throw new MissingRegDocument(regID)
      }
    }
  }

  def deletePAYERegistration(regID: String): Future[Boolean] = {
    registrationRepository.deleteRegistration(regID) map {
      case true => true
      case false => throw new MissingRegDocument(regID)
    }
  }
}
