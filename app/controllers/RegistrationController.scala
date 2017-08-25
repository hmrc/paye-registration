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

import common.exceptions.RegistrationExceptions.{RegistrationFormatException, UnmatchedStatusException}
import common.exceptions.SubmissionExceptions.{ErrorRegistrationException, RegistrationInvalidStatus}
import common.exceptions.SubmissionMarshallingException
import connectors.{AuthConnect, AuthConnector}
import enums.PAYEStatus
import models._
import play.api.mvc._
import services._
import uk.gov.hmrc.play.microservice.controller.BaseController
import auth._
import javax.inject.{Inject, Singleton}

import common.exceptions.DBExceptions.{MissingRegDocument, RetrieveFailed, UpdateFailed}
import models.incorporation.IncorpStatusUpdate
import models.validation.APIValidation
import play.api.Logger
import play.api.libs.json._
import repositories.RegistrationMongoRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class RegistrationController @Inject()(injAuthConnector: AuthConnector,
                                       injRegistrationService: RegistrationService,
                                       injSubmissionService: SubmissionService,
                                       injNotificationService: NotificationService,
                                       injIICounterService: IICounterService) extends RegistrationCtrl {
  val auth: AuthConnect = injAuthConnector
  val registrationService: RegistrationService = injRegistrationService
  val resourceConn: RegistrationMongoRepository = injRegistrationService.registrationRepository
  val submissionService: SubmissionService = injSubmissionService
  val notificationService: NotificationService = injNotificationService
  val counterService: IICounterService = injIICounterService
}

trait RegistrationCtrl extends BaseController with Authenticated with Authorisation[String] {

  val registrationService: RegistrationSrv
  val submissionService: SubmissionSrv
  val notificationService: NotificationService
  val counterService: IICounterSrv

  val companyDetailsAPIFormat = CompanyDetails.formatter(APIValidation)

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
            case Some(companyDetails) => Ok(Json.toJson(companyDetails)(companyDetailsAPIFormat))
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
              Ok(Json.toJson(companyDetailsResponse)(companyDetailsAPIFormat))
            } recover {
              case missing   : MissingRegDocument          => NotFound
              case noContact : RegistrationFormatException => BadRequest(noContact.getMessage)
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
            case Some(employment) => Ok(Json.toJson(employment)(Employment.format(APIValidation)))
            case None             => NotFound
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
              Ok(Json.toJson(employmentResponse)(Employment.format(APIValidation)))
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
            case directors: Seq[Director]      => Ok(Json.toJson(directors)(Director.directorSequenceWriter(APIValidation)))
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [getDirectors] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [getDirectors] User logged in but not authorised for resource $regID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) =>
          Logger.info(s"[RegistrationController] [getDirectors] Auth resource not found for $regID")
          Future.successful(NotFound)
      }
  }

  def upsertDirectors(regID: String) : Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authorised(regID) {
        case Authorised(_) =>
          withJsonBody[Seq[Director]] { directors =>
            registrationService.upsertDirectors(regID, directors) map { directorsResponse =>
              Ok(Json.toJson(directorsResponse)(Director.directorSequenceWriter(APIValidation)))
            } recover {
              case missing : MissingRegDocument => NotFound
              case noNinos : RegistrationFormatException => BadRequest(noNinos.getMessage)
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
            case Some(payeContact) => Ok(Json.toJson(payeContact)(PAYEContact.format(APIValidation)))
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
              Ok(Json.toJson(payeContactResponse)(PAYEContact.format(APIValidation)))
            } recover {
              case missing : MissingRegDocument => NotFound
              case format  : RegistrationFormatException => BadRequest(format.getMessage)
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
          implicit val reads = APIValidation.completionCapacityReads
          withJsonBody[String] { capacity =>
            registrationService.upsertCompletionCapacity(regID, capacity) map { capacityResponse =>
              Ok(Json.toJson(capacityResponse))
            } recover {
              case missing : MissingRegDocument => NotFound
              case format  : RegistrationFormatException => BadRequest(format.getMessage)
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
          submissionService.submitToDes(regID) map (ackRef => Ok(Json.toJson(ackRef))) recover {
            case _: RejectedIncorporationException => NoContent
            case ex: SubmissionMarshallingException => BadRequest(s"Registration was submitted without full data: ${ex.getMessage}")
            case e =>
              Logger.error(s"[RegistrationController] [submitPAYERegistration] Error while submitting to DES the registration with regId $regID - error:", e)
              throw e
          }
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
    implicit request =>
      withJsonBody[IncorpStatusUpdate] { statusUpdate =>
        val transactionId = statusUpdate.transactionId
        registrationService.fetchPAYERegistrationByTransactionID(transactionId) flatMap {
          case None =>
            Logger.error(s"[RegistrationController] [processIncorporationData] No registration found for transaction id $transactionId")
            throw new MissingRegDocument(transactionId)
          case Some(reg) =>
            submissionService.submitTopUpToDES(reg.registrationID, statusUpdate) map (_ => Ok(Json.toJson(statusUpdate.crn)))
      } recoverWith {
        case invalid: ErrorRegistrationException =>
          Future.successful(Ok(s"Cannot process Incorporation Update for transaction ID '$transactionId' - ${invalid.getMessage}"))
        case _: MissingRegDocument =>
          Future.successful(Ok(s"No registration found for transaction id $transactionId"))
        case error: RegistrationInvalidStatus => registrationInvalidStatusHandler(error, transactionId)
        case mongo @ (_: UpdateFailed | _: RetrieveFailed) =>
          Logger.error(s"[RegistrationController] - [processIncorporationData] - Failed to process Incorporation Update for transaction ID '$transactionId' - database error. The update may have completed successfully downstream")
          Future.successful(InternalServerError)
        case e =>
          Logger.error(s"[RegistrationController] [processIncorporationData] Error while processing Incorporation Data for registration with transactionId $transactionId - error: ${e.getMessage}")
          throw e
      }
    }
  }

  def registrationInvalidStatusHandler(regInvalidError: RegistrationInvalidStatus, transactionId: String):Future[Result] ={
    counterService.updateIncorpCount(regInvalidError.regId) map {
        case true => Logger.info(s"[RegistrationController] - [processIncorporationData] - II has called with regID: ${regInvalidError.regId} more than ${counterService.maxIICounterCount} times")
          Ok(s" II has called with regID: ${regInvalidError.regId} more than ${counterService.maxIICounterCount} times")
        case false => Logger.warn(s"[RegistrationController] - [processIncorporationData] - Warning cannot process Incorporation Update for transaction ID '$transactionId' - ${regInvalidError.getMessage}")
          InternalServerError
      } recover {
      case error: UpdateFailed =>
        Logger.error(s"[RegistrationController] - [processIncorporation] returned a None when trying to upsert ${regInvalidError.regId} for transaction ID '$transactionId' - ${regInvalidError.getMessage}")
        InternalServerError
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
          Logger.info(s"[RegistrationController] [updateEligibility] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [updateEligibility] User logged in but not authorised for resource $regId")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def updateRegistrationWithEmpRef(ackref: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[EmpRefNotification] { notification =>
        notificationService.processNotification(ackref, notification) map { updated =>
          Ok(Json.toJson(updated))
        } recover {
          case missing: MissingRegDocument => NotFound(s"No PAYE registration document found for acknowledgement reference $ackref")
        }
      }
  }

  def getDocumentStatus(regId: String): Action[AnyContent] = Action.async {
    implicit request =>
      authorised(regId) {
        case Authorised(_) =>
          registrationService.getStatus(regId) map { status =>
            Ok(Json.toJson(status))
          } recover {
            case missing: MissingRegDocument => NotFound(s"No PAYE registration document found for registration ID $regId")
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [getDocumentStatus] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [getDocumentStatus] User logged in but not authorised for resource $regId")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def deletePAYERegistration(regId: String): Action[AnyContent] = Action.async {
    implicit request =>
      authorised(regId) {
        case Authorised(_) =>
          registrationService.deletePAYERegistration(regId, PAYEStatus.rejected) map { deleted =>
            if(deleted) Ok else InternalServerError
          } recover {
            case _: UnmatchedStatusException => PreconditionFailed
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[RegistrationController] [deletePAYERegistration] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[RegistrationController] [deletePAYERegistration] User logged in but not authorised for resource $regId")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }
}
