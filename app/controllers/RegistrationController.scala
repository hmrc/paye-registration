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

import connectors.{AuthConnect, AuthConnector}
import models.{CompanyDetails, Director, Employment, PAYEContact, SICCode}
import play.api.mvc._
import services._
import uk.gov.hmrc.play.microservice.controller.BaseController
import auth._
import javax.inject.{Inject, Singleton}

import common.exceptions.DBExceptions.MissingRegDocument
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import repositories.RegistrationMongoRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class RegistrationController @Inject()(authConnector: AuthConnector, registrationService: RegistrationService) extends RegistrationCtrl {
  val auth: AuthConnect = authConnector
  val registrationSrv: RegistrationService = registrationService
  val resourceConn: RegistrationMongoRepository = registrationService.registrationRepository
}

trait RegistrationCtrl extends BaseController with Authenticated with Authorisation[String] {

  val registrationSrv: RegistrationService

  def newPAYERegistration(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      authenticated {
        case NotLoggedIn => Future.successful(Forbidden)
        case LoggedIn(context) =>
          registrationSrv.createNewPAYERegistration(regID, context.ids.internalId) map {
            reg => Ok(Json.toJson(reg))
          }
      }
  }

  def getPAYERegistration(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          registrationSrv.fetchPAYERegistration(regID) map {
            case Some(registration) => Ok(Json.toJson(registration))
            case None => NotFound
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [getPAYERegistration] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [getPAYERegistration] User logged in but not authorised for resource $regID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def getCompanyDetails(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          registrationSrv.getCompanyDetails(regID) map {
            case Some(companyDetails) => Ok(Json.toJson(companyDetails))
            case None => NotFound
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [getCompanyDetails] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [getCompanyDetails] User logged in but not authorised for resource $regID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def upsertCompanyDetails(regID: String) : Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          withJsonBody[CompanyDetails] { companyDetails =>
            registrationSrv.upsertCompanyDetails(regID, companyDetails) map { companyDetailsResponse =>
              Ok(Json.toJson(companyDetailsResponse))
            } recover {
              case missing : MissingRegDocument => NotFound
            }
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [upsertCompanyDetails] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [upsertCompanyDetails] User logged in but not authorised for resource $regID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def getEmployment(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          registrationSrv.getEmployment(regID) map {
            case Some(employment) => Ok(Json.toJson(employment))
            case None => NotFound
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [getEmployment] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [getEmployment] User logged in but not authorised for resource $regID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def upsertEmployment(regID: String) : Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          withJsonBody[Employment] { employmentDetails =>
            registrationSrv.upsertEmployment(regID, employmentDetails) map { employmentResponse =>
              Ok(Json.toJson(employmentResponse))
            } recover {
              case missing : MissingRegDocument => NotFound
            }
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [upsertEmployment] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [upsertEmployment] User logged in but not authorised for resource $regID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def getDirectors(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          registrationSrv.getDirectors(regID) map {
            case s: Seq[Director] if s.isEmpty => NotFound
            case directors: Seq[Director] => Ok(Json.toJson(directors))
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [getDirectors] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [getDirectors] User logged in but not authorised for resource $regID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def upsertDirectors(regID: String) : Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          withJsonBody[Seq[Director]] { directors =>
            registrationSrv.upsertDirectors(regID, directors) map { directorsResponse =>
              Ok(Json.toJson(directorsResponse))
            } recover {
              case missing : MissingRegDocument => NotFound
            }
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [upsertDirectors] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [upsertDirectors] User logged in but not authorised for resource $regID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def getSICCodes(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          registrationSrv.getSICCodes(regID) map {
            case s: Seq[SICCode] if s.isEmpty => NotFound
            case sicCodes: Seq[SICCode] => Ok(Json.toJson(sicCodes))
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [getSICCodes] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [getSICCodes] User logged in but not authorised for resource $regID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def upsertSICCodes(regID: String) : Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          withJsonBody[Seq[SICCode]] { sicCodes =>
            registrationSrv.upsertSICCodes(regID, sicCodes) map { sicCodesResponse =>
              Ok(Json.toJson(sicCodesResponse))
            } recover {
              case missing : MissingRegDocument => NotFound
            }
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [upsertSICCodes] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [upsertSICCodes] User logged in but not authorised for resource $regID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def getPAYEContact(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          registrationSrv.getPAYEContact(regID) map {
            case Some(payeContact) => Ok(Json.toJson(payeContact))
            case None => NotFound
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [getPAYEContact] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [getPAYEContact] User logged in but not authorised for resource $regID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def upsertPAYEContact(regID: String) : Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          withJsonBody[PAYEContact] { payeContact =>
            registrationSrv.upsertPAYEContact(regID, payeContact) map { payeContactResponse =>
              Ok(Json.toJson(payeContactResponse))
            } recover {
              case missing : MissingRegDocument => NotFound
            }
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [upsertPAYEContact] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [upsertPAYEContact] User logged in but not authorised for resource $regID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }
}
