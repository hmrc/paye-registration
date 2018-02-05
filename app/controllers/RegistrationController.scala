/*
 * Copyright 2018 HM Revenue & Customs
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
import config.AuthClientConnector
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
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

@Singleton
class RegistrationController @Inject()(injRegistrationService: RegistrationService,
                                       injSubmissionService: SubmissionService,
                                       injNotificationService: NotificationService,
                                       injIICounterService: IICounterService) extends RegistrationCtrl {
  override lazy val authConnector: AuthConnector = AuthClientConnector

  val registrationService: RegistrationService = injRegistrationService
  val resourceConn: RegistrationMongoRepository = injRegistrationService.registrationRepository
  val submissionService: SubmissionService = injSubmissionService
  val notificationService: NotificationService = injNotificationService
  val counterService: IICounterService = injIICounterService
}

trait RegistrationCtrl extends BaseController with Authorisation {

  val registrationService: RegistrationSrv
  val submissionService: SubmissionSrv
  val notificationService: NotificationService
  val counterService: IICounterSrv

  def newPAYERegistration(regID: String) : Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      isAuthenticated { internalId =>
        withJsonBody[String] { transactionID =>
          registrationService.createNewPAYERegistration(regID, transactionID, internalId) map {
            reg => Ok(Json.toJson(reg))
          }
        }
      } recoverWith {
        case _ => Future.successful(Forbidden)
      }
  }

  def getPAYERegistration(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "RegistrationCtrl", "getPAYERegistration") {
          registrationService.fetchPAYERegistration(regID) map {
            case Some(registration) => Ok(Json.toJson(registration))
            case None => NotFound
          }
        }
      }
  }

  def getCompanyDetails(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "RegistrationCtrl", "getCompanyDetails") {
          registrationService.getCompanyDetails(regID) map {
            case Some(companyDetails) => Ok(Json.toJson(companyDetails))
            case None => NotFound
          }
        }
      }
  }

  def upsertCompanyDetails(regID: String) : Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "RegistrationCtrl", "upsertCompanyDetails") {
          withJsonBody[CompanyDetails] { companyDetails =>
            registrationService.upsertCompanyDetails(regID, companyDetails) map { companyDetailsResponse =>
              Ok(Json.toJson(companyDetailsResponse))
            } recover {
              case missing   : MissingRegDocument          => NotFound
              case noContact : RegistrationFormatException => BadRequest(noContact.getMessage)
            }
          }
        }
      }
  }

  def getEmployment(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "RegistrationCtrl", "getEmployment") {
          registrationService.getEmployment(regID) map {
            case Some(employment) => Ok(Json.toJson(employment)(Employment.format(APIValidation)))
            case None             => NotFound
          }
        }
      }
  }

  def upsertEmployment(regID: String) : Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "RegistrationCtrl", "upsertEmployment") {
          withJsonBody[Employment] { employmentDetails =>
            registrationService.upsertEmployment(regID, employmentDetails) map { employmentResponse =>
              Ok(Json.toJson(employmentResponse)(Employment.format(APIValidation)))
            } recover {
              case missing : MissingRegDocument => NotFound
            }
          }
        }
      }
  }

  def getDirectors(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "RegistrationCtrl", "getDirectors") {
          registrationService.getDirectors(regID) map {
            case s: Seq[Director] if s.isEmpty => NotFound
            case directors: Seq[Director]      => Ok(Json.toJson(directors)(Director.directorSequenceWriter(APIValidation)))
          }
        }
      }
  }

  def upsertDirectors(regID: String) : Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "RegistrationCtrl", "upsertDirectors") {
          withJsonBody[Seq[Director]] { directors =>
            registrationService.upsertDirectors(regID, directors) map { directorsResponse =>
              Ok(Json.toJson(directorsResponse)(Director.directorSequenceWriter(APIValidation)))
            } recover {
              case missing : MissingRegDocument => NotFound
              case noNinos : RegistrationFormatException => BadRequest(noNinos.getMessage)
            }
          }
        }
      }
  }

  def getSICCodes(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "RegistrationCtrl", "getSICCodes") {
          registrationService.getSICCodes(regID) map {
            case s: Seq[SICCode] if s.isEmpty => NotFound
            case sicCodes: Seq[SICCode] => Ok(Json.toJson(sicCodes))
          }
        }
      }
  }

  def upsertSICCodes(regID: String) : Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "RegistrationCtrl", "upsertSICCodes") {
          withJsonBody[Seq[SICCode]] { sicCodes =>
            registrationService.upsertSICCodes(regID, sicCodes) map { sicCodesResponse =>
              Ok(Json.toJson(sicCodesResponse))
            } recover {
              case missing : MissingRegDocument => NotFound
            }
          }
        }
      }
  }

  def getPAYEContact(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "RegistrationCtrl", "getPAYEContact") {
          registrationService.getPAYEContact(regID) map {
            case Some(payeContact) => Ok(Json.toJson(payeContact)(PAYEContact.format(APIValidation)))
            case None => NotFound
          }
        }
      }
  }

  def upsertPAYEContact(regID: String) : Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "RegistrationCtrl", "upsertPAYEContact") {
          withJsonBody[PAYEContact] { payeContact =>
            registrationService.upsertPAYEContact(regID, payeContact) map { payeContactResponse =>
              Ok(Json.toJson(payeContactResponse)(PAYEContact.format(APIValidation)))
            } recover {
              case missing : MissingRegDocument => NotFound
              case format  : RegistrationFormatException => BadRequest(format.getMessage)
            }
          }
        }
      }
  }

  def getCompletionCapacity(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "RegistrationCtrl", "getCompletionCapacity") {
          registrationService.getCompletionCapacity(regID) map {
            case Some(capacity) => Ok(Json.toJson(capacity))
            case None => NotFound
          }
        }
      }
  }

  def upsertCompletionCapacity(regID: String) : Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "RegistrationCtrl", "upsertCompletionCapacity") {
          implicit val stringReads: Reads[String] = APIValidation.completionCapacityReads
          withJsonBody[String] { capacity =>
            registrationService.upsertCompletionCapacity(regID, capacity) map { capacityResponse =>
              Ok(Json.toJson(capacityResponse))
            } recover {
              case missing : MissingRegDocument => NotFound
              case format  : RegistrationFormatException => BadRequest(format.getMessage)
            }
          }
        }
      }
  }

  def submitPAYERegistration(regID: String): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "RegistrationCtrl", "submitPAYERegistration") {
          submissionService.submitToDes(regID) map (ackRef => Ok(Json.toJson(ackRef))) recover {
            case _: RejectedIncorporationException => NoContent
            case ex: SubmissionMarshallingException => BadRequest(s"Registration was submitted without full data: ${ex.getMessage}")
            case e =>
              Logger.error(s"[RegistrationController] [submitPAYERegistration] Error while submitting to DES the registration with regId $regID", e)
              throw e
          }
        }
      }
  }

  def getAcknowledgementReference(regID: String) : Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "RegistrationCtrl", "getAcknowledgementReference") {
          registrationService.getAcknowledgementReference(regID) map {
            case Some(ackRef) => Ok(Json.toJson(ackRef))
            case None => NotFound
          }
        }
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

  def registrationInvalidStatusHandler(regInvalidError: RegistrationInvalidStatus, transactionId: String)(implicit hc: HeaderCarrier):Future[Result] ={
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
      isAuthorised(regId) { authResult =>
        authResult.ifAuthorised(regId, "RegistrationCtrl", "getEligibility") {
          registrationService.getEligibility(regId) map {
            case Some(eligibility) => Ok(Json.toJson(eligibility))
            case None => NotFound
          }
        }
      }
  }

  def updateEligibility(regId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      isAuthorised(regId) { authResult =>
        authResult.ifAuthorised(regId, "RegistrationCtrl", "updateEligibility") {
          withJsonBody[Eligibility] { json =>
            registrationService.updateEligibility(regId, json) map { updated =>
              Ok(Json.toJson(updated))
            } recover {
              case missing : MissingRegDocument => NotFound
            }
          }
        }
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
      isAuthorised(regId) { authResult =>
        authResult.ifAuthorised(regId, "RegistrationCtrl", "getDocumentStatus") {
          registrationService.getStatus(regId) map { status =>
            Ok(Json.toJson(status))
          } recover {
            case missing: MissingRegDocument => NotFound(s"No PAYE registration document found for registration ID $regId")
          }
        }
      }
  }

  def deletePAYERegistration(regId: String): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regId) { authResult =>
        authResult.ifAuthorised(regId, "RegistrationCtrl", "deletePAYERegistration") {
          registrationService.deletePAYERegistration(regId, PAYEStatus.rejected) map { deleted =>
            if(deleted) Ok else InternalServerError
          } recover {
            case _: UnmatchedStatusException => PreconditionFailed
          }
        }
      }
  }
}
