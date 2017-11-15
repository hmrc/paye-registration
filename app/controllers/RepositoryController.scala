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

package controllers

import javax.inject.{Inject, Singleton}

import auth._
import common.exceptions.RegistrationExceptions.UnmatchedStatusException
import connectors.{AuthConnect, AuthConnector}
import enums.PAYEStatus
import play.api.mvc.{Action, AnyContent}
import repositories.RegistrationMongoRepository
import services.RegistrationService
import uk.gov.hmrc.play.microservice.controller.BaseController

import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import scala.concurrent.Future

@Singleton
class RepositoryController @Inject()(injAuthConnector: AuthConnector,
                                     injRegistrationService: RegistrationService) extends RepositoryCtrl {
  val auth: AuthConnect = injAuthConnector
  val resourceConn: RegistrationMongoRepository = injRegistrationService.registrationRepository
  val registraitonService: RegistrationService = injRegistrationService
}

trait RepositoryCtrl extends BaseController with Authenticated with Authorisation[String] {

  val registraitonService: RegistrationService

  def deleteRegistrationFromDashboard(regId: String) : Action[AnyContent] = Action.async {
    implicit request =>
      authorised(regId) {
        case Authorised(_) => registraitonService.deletePAYERegistration(regId, PAYEStatus.draft, PAYEStatus.invalid) map { deleted =>
          if(deleted) Ok else InternalServerError
        } recover {
          case _: UnmatchedStatusException => PreconditionFailed
        }
        case NotLoggedInOrAuthorised => Future.successful(Forbidden)
        case NotAuthorised(_) => Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

}
