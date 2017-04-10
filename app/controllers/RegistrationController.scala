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

import common.exceptions.SubmissionExceptions.InvalidRegistrationException
import connectors.{AuthConnect, AuthConnector}
import enums.PAYEStatus
import models._
import play.api.mvc._
import services._
import uk.gov.hmrc.play.microservice.controller.BaseController
import auth._
import javax.inject.{Inject, Singleton}

import common.exceptions.DBExceptions.{MissingRegDocument, UpdateFailed}
import models.incorporation.IncorpStatusUpdate
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import repositories.RegistrationMongoRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class RegistrationController @Inject()(injAuthConnector: AuthConnector,
                                       injRegistrationService: RegistrationService,
                                       injSubmissionService: SubmissionService) extends RegistrationCtrl {
  val auth: AuthConnect = injAuthConnector
  val registrationService: RegistrationService = injRegistrationService
  val resourceConn: RegistrationMongoRepository = injRegistrationService.registrationRepository
  val submissionService: SubmissionService = injSubmissionService
}

trait RegistrationCtrl extends BaseController with Authenticated with Authorisation[String] {

  val registrationService: RegistrationSrv
  val submissionService: SubmissionSrv

  def newPAYERegistration(regID: String) : Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authenticated {
        case NotLoggedIn => Future.successful(Forbidden)
        case LoggedIn(context) =>
          withJsonBody[String] { transactionID =>
            registrationService.createNewPAYERegistration(regID, transactionID, context.ids.internalId) map {
              reg => Ok(Json.toJson(reg))
            }
          }
      }
  }

  def getPAYERegistration(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          registrationService.fetchPAYERegistration(regID) map {
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
          registrationService.getCompanyDetails(regID) map {
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
            registrationService.upsertCompanyDetails(regID, companyDetails) map { companyDetailsResponse =>
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
          registrationService.getEmployment(regID) map {
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
            registrationService.upsertEmployment(regID, employmentDetails) map { employmentResponse =>
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
          registrationService.getDirectors(regID) map {
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
            registrationService.upsertDirectors(regID, directors) map { directorsResponse =>
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
          registrationService.getSICCodes(regID) map {
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
            registrationService.upsertSICCodes(regID, sicCodes) map { sicCodesResponse =>
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
          registrationService.getPAYEContact(regID) map {
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
            registrationService.upsertPAYEContact(regID, payeContact) map { payeContactResponse =>
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

  def getCompletionCapacity(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          registrationService.getCompletionCapacity(regID) map {
            case Some(capacity) => Ok(Json.toJson(capacity))
            case None => NotFound
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [getCompletionCapacity] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [getCompletionCapacity] User logged in but not authorised for resource $regID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def upsertCompletionCapacity(regID: String) : Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          withJsonBody[String] { capacity =>
            registrationService.upsertCompletionCapacity(regID, capacity) map { capacityResponse =>
              Ok(Json.toJson(capacityResponse))
            } recover {
              case missing : MissingRegDocument => NotFound
            }
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [upsertCompletionCapacity] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [upsertCompletionCapacity] User logged in but not authorised for resource $regID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def submitPAYERegistration(regID: String): Action[AnyContent] = Action.async {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          submissionService.submitPartialToDES(regID) map (ackRef => Ok(Json.toJson(ackRef)))
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [submitPAYERegistration] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [submitPAYERegistration] User logged in but not authorised for resource $regID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def getAcknowledgementReference(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          registrationService.getAcknowledgementReference(regID) map {
            case Some(ackRef) => Ok(Json.toJson(ackRef))
            case None => NotFound
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [getAcknowledgementReference] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [getAcknowledgementReference] User logged in but not authorised for resource $regID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
    }

  def processIncorporationData : Action[JsValue] = Action.async(parse.json) {
    implicit request => {
      val transactionId = request.body.as[IncorpStatusUpdate].transactionId
      registrationService.fetchPAYERegistrationByTransactionID(transactionId) flatMap {
        case None => Future.successful(NotFound(s"No registration found for transaction id $transactionId"))
        case Some(reg) => withJsonBody[IncorpStatusUpdate] { incorpStatusUpdateData =>
          submissionService.submitTopUpToDES(reg.registrationID, incorpStatusUpdateData) map (_ => Ok(Json.toJson(incorpStatusUpdateData.crn)))
        }
      } recover {
        case invalid: InvalidRegistrationException => InternalServerError(
                s"Cannot process Incorporation Update for transaction ID '$transactionId' - attempting to submit Top Up when status is not '${PAYEStatus.held}'"
              )
        case mongo @ (_: UpdateFailed | _: MissingRegDocument) => InternalServerError(
                s"Failed to process Incorporation Update for transaction ID '$transactionId' - database error. The update may have completed successfully downstream"
              )
        case e => throw e
      }
    }
  }

  def getEligibility(regId: String): Action[AnyContent] = Action.async {
    implicit request =>
      authorised(regId) {
        case Authorised(_) =>
          registrationService.getEligibility(regId) map {
            case Some(eligibility) => Ok(Json.toJson(eligibility))
            case None => NotFound
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [getEligibility] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [getEligibility] User logged in but not authorised for resource $regId")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def updateEligibility(regId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authorised(regId) {
        case Authorised(_) =>
          withJsonBody[Eligibility] { json =>
            registrationService.updateEligibility(regId, json) map { updated =>
              Ok(Json.toJson(updated))
            } recover {
              case missing : MissingRegDocument => NotFound
            }
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [getEligibility] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [getEligibility] User logged in but not authorised for resource $regId")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def updateRegistrationWithEmpRef(ackRef: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[EmpRefNotification] { notification =>
        registrationService.updateEmpRef(ackRef, notification) map { updated =>
          Ok(Json.toJson(updated))
        } recover {
          case missing: MissingRegDocument => NotFound
          case _ => InternalServerError
        }
      }
  }
}
