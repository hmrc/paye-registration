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

import models.{CompanyDetails, Director, Employment, PAYEContact, PAYERegistration, SICCode}
import repositories.{RegistrationMongo, RegistrationMongoRepository, RegistrationRepository}
import play.api.Logger

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class RegistrationService @Inject()(injRegistrationMongoRepository: RegistrationMongo) extends RegistrationSrv {
  val registrationRepository : RegistrationMongoRepository = injRegistrationMongoRepository.store
}

trait RegistrationSrv {

  val registrationRepository : RegistrationRepository

  def createNewPAYERegistration(regID: String, internalId : String): Future[PAYERegistration] = {
    registrationRepository.retrieveRegistration(regID) flatMap {
      case None => registrationRepository.createNewRegistration(regID, internalId)
      case Some(registration) =>
        Logger.info(s"Cannot create new registration for reg ID '$regID' as registration already exists")
        Future.successful(registration)
    }
  }

  def fetchPAYERegistration(regID: String): Future[Option[PAYERegistration]] = {
    registrationRepository.retrieveRegistration(regID)
  }

  def getCompanyDetails(regID: String): Future[Option[CompanyDetails]] = {
    registrationRepository.retrieveCompanyDetails(regID)
  }

  def upsertCompanyDetails(regID: String, companyDetails: CompanyDetails): Future[CompanyDetails] = {
    registrationRepository.upsertCompanyDetails(regID, companyDetails)
  }

  def getEmployment(regID: String): Future[Option[Employment]] = {
    registrationRepository.retrieveEmployment(regID)
  }

  def upsertEmployment(regID: String, employmentDetails: Employment): Future[Employment] = {
    registrationRepository.upsertEmployment(regID, employmentDetails)
  }

  def getDirectors(regID: String): Future[Seq[Director]] = {
    registrationRepository.retrieveDirectors(regID)
  }

  def upsertDirectors(regID: String, directors: Seq[Director]): Future[Seq[Director]] = {
    registrationRepository.upsertDirectors(regID, directors)
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
    registrationRepository.upsertPAYEContact(regID, payeContact)
  }

  def getCompletionCapacity(regID: String): Future[Option[String]] = {
    registrationRepository.retrieveCompletionCapacity(regID)
  }

  def upsertCompletionCapacity(regID: String, capacity: String): Future[String] = {
    registrationRepository.upsertCompletionCapacity(regID, capacity)
  }
}
