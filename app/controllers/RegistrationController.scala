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

import connectors.AuthConnector
import models.{PAYERegistration, CompanyDetails}
import common.exceptions.InternalExceptions._
import play.api.Logger
import play.api.mvc._
import services._
import uk.gov.hmrc.play.microservice.controller.BaseController
import auth._
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object RegistrationController extends RegistrationController {
  //$COVERAGE-OFF$
  override val auth = AuthConnector
  override val registrationService = RegistrationService
  //$COVERAGE-ON$
}

trait RegistrationController extends BaseController with Authenticated {

  val registrationService: RegistrationService

  def newPAYERegistration(regID: String) = Action.async {
    implicit request =>
      authenticated {
        case NotLoggedIn => Future.successful(Forbidden)
        case LoggedIn(context) => registrationService.createNewPAYERegistration(regID) map {
          case DBDuplicateResponse => BadRequest(s"PAYE Registration already exists for registration ID $regID")
          case DBErrorResponse(e) => InternalServerError(e.getMessage)
          case DBSuccessResponse(registration: PAYERegistration) => Ok(Json.toJson(registration).as[JsObject])
          case DBSuccessResponse(resp) => throw new IncorrectDBSuccessResponseException(expected = PAYERegistration, actual = resp)
          case otherResp => throw new UnexpextedDBResponseException("newPAYERegistration", otherResp)
      }
    }
  }

  def getPAYERegistration(regID: String) = Action.async {
    implicit request =>
      authenticated {
        case NotLoggedIn => Future.successful(Forbidden)
        case LoggedIn(context) => registrationService.fetchPAYERegistration(regID) map {
          case DBNotFoundResponse => NotFound
          case DBErrorResponse(e) => InternalServerError(e.getMessage)
          case DBSuccessResponse(registration: PAYERegistration) => Ok(Json.toJson(registration).as[JsObject])
          case DBSuccessResponse(resp) => throw new IncorrectDBSuccessResponseException(expected = PAYERegistration, actual = resp)
          case otherResp => throw new UnexpextedDBResponseException("getPAYERegistration", otherResp)
        }
      }
  }

  def upsertCompanyDetails(regID: String) = Action.async(parse.json) {
    implicit request =>
      authenticated {
        case NotLoggedIn => Future.successful(Forbidden)
        case LoggedIn(context) =>
          withJsonBody[CompanyDetails] {
            companyDetails =>
              registrationService.upsertCompanyDetails(regID, companyDetails) map {
                case DBNotFoundResponse => NotFound
                case DBErrorResponse(e) => InternalServerError(e.getMessage)
                case DBSuccessResponse(registration: CompanyDetails) => Ok(Json.toJson(registration).as[JsObject])
                case DBSuccessResponse(resp) => throw new IncorrectDBSuccessResponseException(expected = CompanyDetails, actual = resp)
                case otherResp => throw new UnexpextedDBResponseException("upsertCompanyDetails", otherResp)
              }
          }
      }
  }

}
