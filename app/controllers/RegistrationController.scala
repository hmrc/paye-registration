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
import models.{CompanyDetails, Employment}
import play.api.mvc._
import services._
import uk.gov.hmrc.play.microservice.controller.BaseController
import auth._
import common.exceptions.DBExceptions.MissingRegDocument
import play.api.libs.json.Json

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
        case LoggedIn(context) =>
          registrationService.createNewPAYERegistration(regID) map {
            reg => Ok(Json.toJson(reg))
          }
    }
  }

  def getPAYERegistration(regID: String) = Action.async {
    implicit request =>
      authenticated {
        case NotLoggedIn => Future.successful(Forbidden)
        case LoggedIn(context) => registrationService.fetchPAYERegistration(regID) map {
          case Some(registration) => Ok(Json.toJson(registration))
          case None => NotFound
        }
      }
  }

  def getCompanyDetails(regID: String) = Action.async {
    implicit request =>
      authenticated {
        case NotLoggedIn => Future.successful(Forbidden)
        case LoggedIn(context) => registrationService.getCompanyDetails(regID) map {
            case Some(companyDetails) => Ok(Json.toJson(companyDetails))
            case None => NotFound
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
                companyDetailsResponse =>
                  Ok(Json.toJson(companyDetailsResponse))
              } recover {
                case missing: MissingRegDocument => NotFound
              }
          }
      }
  }

  def getEmployment(regID: String) = Action.async {
    implicit request =>
      authenticated {
        case NotLoggedIn => Future.successful(Forbidden)
        case LoggedIn(context) => registrationService.getEmployment(regID) map {
            case Some(employment) => Ok(Json.toJson(employment))
            case None => NotFound
          }
      }
  }

  def upsertEmployment(regID: String) = Action.async(parse.json) {
    implicit request =>
      authenticated {
        case NotLoggedIn => Future.successful(Forbidden)
        case LoggedIn(context) =>
          withJsonBody[Employment] {
            employmentDetails =>
              registrationService.upsertEmployment(regID, employmentDetails) map {
                employmentResponse =>
                  Ok(Json.toJson(employmentResponse))
              } recover {
                case missing: MissingRegDocument => NotFound
              }
          }
      }
  }

}
