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

import models.{CompanyDetails, PAYERegistration, Employment}
import repositories.RegistrationMongoRepository
import play.api.Logger

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object RegistrationService extends RegistrationService {
  //$COVERAGE-OFF$
  override val registrationRepository = repositories.RegistrationMongo.store
  //$COVERAGE-ON$
}

trait RegistrationService {

  val registrationRepository: RegistrationMongoRepository

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

}
