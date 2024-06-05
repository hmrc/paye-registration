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

package controllers

import auth._
import common.exceptions.RegistrationExceptions.UnmatchedStatusException
import enums.PAYEStatus
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.RegistrationMongoRepository
import services.RegistrationService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class RepositoryController @Inject()(registrationService: RegistrationService,
                                     val authConnector: AuthConnector,
                                     controllerComponents: ControllerComponents
                                    )(implicit ec: ExecutionContext) extends BackendController(controllerComponents) with Authorisation {

  val resourceConn: RegistrationMongoRepository = registrationService.registrationRepository

  def deleteRegistrationFromDashboard(regId: String): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regId) { authResult =>
        authResult.ifAuthorised(regId, "deleteRegistrationFromDashboard") {
          registrationService.deletePAYERegistration(regId, PAYEStatus.draft, PAYEStatus.invalid) map { deleted =>
            if (deleted) Ok else InternalServerError
          } recover {
            case _: UnmatchedStatusException => PreconditionFailed
          }
        }
      }
  }

}
