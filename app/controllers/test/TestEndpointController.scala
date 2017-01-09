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

package controllers.test

import auth.{NotLoggedIn, Authenticated}
import connectors.AuthConnector
import play.api.mvc.Action
import repositories.RegistrationMongoRepository
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object TestEndpointController extends TestEndpointController {
  //$COVERAGE-OFF$
  override val auth = AuthConnector
  override val registrationRepository = repositories.RegistrationMongo.store
  //$COVERAGE-ON$
}

trait TestEndpointController extends BaseController with Authenticated {

  val registrationRepository: RegistrationMongoRepository

  def registrationTeardown = Action.async {
    registrationRepository.dropCollection map {
      case _ => Ok
    } recover {
      case e => InternalServerError(e.getMessage)
    }
  }

  def deleteRegistration(regID: String) = Action.async {
    registrationRepository.deleteRegistration(regID) map {
      case _ => Ok
    } recover {
      case e => InternalServerError(e.getMessage)
    }
  }

}
