/*
 * Copyright 2023 HM Revenue & Customs
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

import auth.CryptoSCRS
import enums.PAYEStatus
import models._
import models.validation.APIValidation
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.RegistrationMongoRepository
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.internalId
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class TestEndpointController @Inject()(registrationRepository: RegistrationMongoRepository,
                                       val authConnector: AuthConnector,
                                       val cryptoSCRS: CryptoSCRS,
                                       controllerComponents: ControllerComponents
                                      ) extends BackendController(controllerComponents) with AuthorisedFunctions {

  def registrationTeardown: Action[AnyContent] = Action.async {
    implicit request =>
      registrationRepository.dropCollection map {
        _ => Ok
      } recover {
        case e => InternalServerError(e.getMessage)
      }
  }

  def deleteRegistration(regID: String): Action[AnyContent] = Action.async {
    implicit request =>
      registrationRepository.deleteRegistration(regID) map {
        _ => Ok
      } recover {
        case e => InternalServerError(e.getMessage)
      }
  }

  def updateRegistration(regID: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      implicit val fmt = PAYERegistration.format(APIValidation, cryptoSCRS)
      authorised().retrieve(internalId) { id =>
        withJsonBody[JsObject] { reg =>
          val regWithId = reg ++ Json.obj("internalID" -> id.getOrElse(throw new Exception("Missing internalId")))
          val regWithStatus = regWithId ++ Json.obj("status" -> "draft")
          val regWithLastUpdate = regWithStatus ++ Json.obj("lastUpdate" -> (reg \ "formCreationTimestamp").as[String])

          regWithLastUpdate.validate[PAYERegistration].fold(
            errs => Future.successful(BadRequest(errs.toString())),
            registration => registrationRepository.updateRegistration(registration) map {
              _ => Ok(Json.toJson(reg).as[JsObject])
            } recover {
              case e => InternalServerError(e.getMessage)
            }
          )
        }
      } recoverWith {
        case _ => Future.successful(Forbidden)
      }
  }

  def newStatus(regId: String, status: String): Action[AnyContent] = Action.async {
    implicit request =>
      registrationRepository.createNewRegistration(regId, regId, "XXXXXX") flatMap { _ =>
        registrationRepository.updateRegistrationStatus(regId, createPAYEStatus(status)) map {
          _ => Ok
        }
      } recover {
        case e => InternalServerError(e.getMessage)
      }

  }

  def updateStatus(regId: String, status: String): Action[AnyContent] = Action.async {
    implicit request =>
      registrationRepository.updateRegistrationStatus(regId, createPAYEStatus(status)) map {
        _ => Ok(s"Status of registration $regId updated to $status")
      }
  }

  private def createPAYEStatus(status: String): PAYEStatus.Value = {
    status.toLowerCase match {
      case "draft" => PAYEStatus.draft
      case "held" => PAYEStatus.held
      case "submitted" => PAYEStatus.submitted
      case "acknowledged" => PAYEStatus.acknowledged
      case "invalid" => PAYEStatus.invalid
      case "cancelled" => PAYEStatus.cancelled
      case "rejected" => PAYEStatus.rejected
      case _ => PAYEStatus.draft
    }
  }
}
