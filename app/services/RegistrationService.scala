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

import common.exceptions.DBExceptions.{MissingRegDocument, PreExistingRegDocument}
import models.{CompanyDetails, PAYERegistration}
import repositories.RegistrationMongoRepository
import play.api.Logger

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

sealed trait DBResponse
case class  DBSuccessResponse[T](responseObject: T) extends DBResponse
case object DBNotFoundResponse                      extends DBResponse
case object DBDuplicateResponse                     extends DBResponse
case class  DBErrorResponse(err: Throwable)         extends DBResponse

object RegistrationService extends RegistrationService {
  //$COVERAGE-OFF$
  override val registrationRepository = repositories.RegistrationMongo.store
  //$COVERAGE-ON$
}

trait RegistrationService {

  val registrationRepository: RegistrationMongoRepository

  def createNewPAYERegistration(regID: String): Future[DBResponse] = {
    registrationRepository.retrieveRegistration(regID) flatMap {
      case Some(registration) =>
        Logger.warn(s"Cannot create new registration for reg ID '$regID' as registration already exists")
        Future.successful(DBDuplicateResponse)
      case None => registrationRepository.createNewRegistration(regID) map {
        reg => DBSuccessResponse[PAYERegistration](reg)
      }
    } recover {
      case e => DBErrorResponse(e)
    }
  }

  def fetchPAYERegistration(regID: String): Future[DBResponse] = {
    registrationRepository.retrieveRegistration(regID) map {
      case Some(registration) => DBSuccessResponse[PAYERegistration](registration)
      case None => DBNotFoundResponse
    } recover {
      case e => DBErrorResponse(e)
    }
  }

  def upsertCompanyDetails(regID: String, companyDetails: CompanyDetails): Future[DBResponse] = {
    registrationRepository.upsertCompanyDetails(regID, companyDetails) map {
      case details => DBSuccessResponse[CompanyDetails](details)
    } recover {
      case missing: MissingRegDocument => DBNotFoundResponse
      case err => DBErrorResponse(err)
    }
  }

}
