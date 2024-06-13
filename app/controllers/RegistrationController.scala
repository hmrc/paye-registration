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
import common.exceptions.DBExceptions.{MissingRegDocument, RetrieveFailed, UpdateFailed}
import common.exceptions.RegistrationExceptions.{RegistrationFormatException, UnmatchedStatusException}
import common.exceptions.SubmissionExceptions.{ErrorRegistrationException, RegistrationInvalidStatus}
import common.exceptions.SubmissionMarshallingException
import enums.PAYEStatus
import models._
import models.incorporation.IncorpStatusUpdate
import models.validation.APIValidation
import utils.Logging
import play.api.libs.json._
import play.api.mvc._
import repositories.RegistrationMongoRepository
import services._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegistrationController @Inject()(registrationService: RegistrationService,
                                       submissionService: SubmissionService,
                                       notificationService: NotificationService,
                                       counterService: IICounterService,
                                       val crypto: CryptoSCRS,
                                       val authConnector: AuthConnector,
                                       controllerComponents: ControllerComponents)(implicit ec: ExecutionContext) extends BackendController(controllerComponents) with Authorisation with Logging {

  val resourceConn: RegistrationMongoRepository = registrationService.registrationRepository

  def newPAYERegistration(regID: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      isAuthenticated { internalId =>
        withJsonBody[String] { transactionID =>
          registrationService.createNewPAYERegistration(regID, transactionID, internalId) map {
            reg => Ok(Json.toJson(reg)(PAYERegistration.format(APIValidation, crypto)))
          }
        }
      } recoverWith {
        case _ => Future.successful(Forbidden)
      }
  }

  def getPAYERegistration(regID: String): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "getPAYERegistration") {
          registrationService.fetchPAYERegistration(regID) map {
            case Some(registration) => Ok(Json.toJson(registration)(PAYERegistration.format(APIValidation, crypto)))
            case None => NotFound
          }
        }
      }
  }

  def getRegistrationId(txId: String): Action[AnyContent] = Action.async { implicit request =>
    registrationService.getRegistrationId(txId) map { regId =>
      Ok(Json.toJson(regId))
    } recover {
      case _: MissingRegDocument =>
        logger.warn(s"[getRegistrationId] No registration found based on txId $txId")
        NotFound
      case _: IllegalStateException =>
        logger.warn(s"[getRegistrationId] Registration found for txId $txId but no regId was found")
        Conflict
      case _ =>
        logger.warn(s"[getRegistrationId] No registration found based on txId $txId")
        InternalServerError
    }
  }

  def getCompanyDetails(regID: String): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "getCompanyDetails") {
          registrationService.getCompanyDetails(regID) map {
            case Some(companyDetails) => Ok(Json.toJson(companyDetails))
            case None => NotFound
          }
        }
      }
  }

  def upsertCompanyDetails(regID: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "upsertCompanyDetails") {
          withJsonBody[CompanyDetails] { companyDetails =>
            registrationService.upsertCompanyDetails(regID, companyDetails) map { companyDetailsResponse =>
              Ok(Json.toJson(companyDetailsResponse))
            } recover {
              case missing: MissingRegDocument => NotFound
              case noContact: RegistrationFormatException => BadRequest(noContact.getMessage)
            }
          }
        }
      }
  }

  def getEmploymentInfo(regID: String): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "getEmploymentInfo") {
          registrationService.getEmploymentInfo(regID) map {
            case Some(employmentInfo) => Ok(Json.toJson(employmentInfo)(EmploymentInfo.apiFormat))
            case None => NoContent
          } recover {
            case missing: MissingRegDocument => NotFound
          }
        }
      }
  }

  def upsertEmploymentInfo(regID: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "upsertEmploymentInfo") {
          withIncorporationDate(regID) { incorpDate =>
            implicit val apiFormat: Format[EmploymentInfo] = EmploymentInfo.format(formatter = APIValidation, incorpDate = incorpDate)
            withJsonBody[EmploymentInfo] { employmentDetails =>
              registrationService.upsertEmploymentInfo(regID, employmentDetails) map { employmentResponse =>
                Ok(Json.toJson(employmentResponse))
              } recover {
                case missing: MissingRegDocument => NotFound
              }
            }
          }
        }
      }
  }

  private def withIncorporationDate(regID: String)(f: Option[LocalDate] => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {
    registrationService.getIncorporationDate(regID) flatMap (f(_))
  }

  def getDirectors(regID: String): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "getDirectors") {
          registrationService.getDirectors(regID) map {
            case s: Seq[Director] if s.isEmpty => NotFound
            case directors: Seq[Director] => Ok(Json.toJson(directors)(Director.directorSequenceWriter(APIValidation)))
          }
        }
      }
  }

  def upsertDirectors(regID: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "upsertDirectors") {
          withJsonBody[Seq[Director]] { directors =>
            registrationService.upsertDirectors(regID, directors) map { directorsResponse =>
              Ok(Json.toJson(directorsResponse)(Director.directorSequenceWriter(APIValidation)))
            } recover {
              case missing: MissingRegDocument => NotFound
              case noNinos: RegistrationFormatException => BadRequest(noNinos.getMessage)
            }
          }
        }
      }
  }

  def getSICCodes(regID: String): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "getSICCodes") {
          registrationService.getSICCodes(regID) map {
            case s: Seq[SICCode] if s.isEmpty => NotFound
            case sicCodes: Seq[SICCode] => Ok(Json.toJson(sicCodes))
          }
        }
      }
  }

  def upsertSICCodes(regID: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "upsertSICCodes") {
          withJsonBody[Seq[SICCode]] { sicCodes =>
            registrationService.upsertSICCodes(regID, sicCodes) map { sicCodesResponse =>
              Ok(Json.toJson(sicCodesResponse))
            } recover {
              case missing: MissingRegDocument => NotFound
            }
          }
        }
      }
  }

  def getPAYEContact(regID: String): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "getPAYEContact") {
          registrationService.getPAYEContact(regID) map {
            case Some(payeContact) => Ok(Json.toJson(payeContact)(PAYEContact.format(APIValidation)))
            case None => NotFound
          }
        }
      }
  }

  def upsertPAYEContact(regID: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "upsertPAYEContact") {
          withJsonBody[PAYEContact] { payeContact =>
            registrationService.upsertPAYEContact(regID, payeContact) map { payeContactResponse =>
              Ok(Json.toJson(payeContactResponse)(PAYEContact.format(APIValidation)))
            } recover {
              case missing: MissingRegDocument => NotFound
              case format: RegistrationFormatException => BadRequest(format.getMessage)
            }
          }
        }
      }
  }

  def getCompletionCapacity(regID: String): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "getCompletionCapacity") {
          registrationService.getCompletionCapacity(regID) map {
            case Some(capacity) => Ok(Json.toJson(capacity))
            case None => NotFound
          }
        }
      }
  }

  def upsertCompletionCapacity(regID: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "upsertCompletionCapacity") {
          implicit val stringReads: Reads[String] = APIValidation.completionCapacityReads
          withJsonBody[String] { capacity =>
            registrationService.upsertCompletionCapacity(regID, capacity) map { capacityResponse =>
              Ok(Json.toJson(capacityResponse))
            } recover {
              case missing: MissingRegDocument => NotFound
              case format: RegistrationFormatException => BadRequest(format.getMessage)
            }
          }
        }
      }
  }

  def submitPAYERegistration(regID: String): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "submitPAYERegistration") {
          submissionService.submitToDes(regID) map (ackRef => Ok(Json.toJson(ackRef))) recover {
            case _: RejectedIncorporationException => NoContent
            case ex: SubmissionMarshallingException => BadRequest(s"Registration was submitted without full data: ${ex.getMessage}")
            case e =>
              logger.error(s"[submitPAYERegistration] Error while submitting to DES the registration with regId $regID", e)
              throw e
          }
        }
      }
  }

  def getAcknowledgementReference(regID: String): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regID) { authResult =>
        authResult.ifAuthorised(regID, "getAcknowledgementReference") {
          registrationService.getAcknowledgementReference(regID) map {
            case Some(ackRef) => Ok(Json.toJson(ackRef))
            case None => NotFound
          }
        }
      }
  }

  def processIncorporationData: Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[IncorpStatusUpdate] { statusUpdate =>
        val transactionId = statusUpdate.transactionId
        registrationService.fetchPAYERegistrationByTransactionID(transactionId) flatMap {
          case None =>
            logger.error(s"[processIncorporationData] No registration found for transaction id $transactionId")
            throw new MissingRegDocument(transactionId)
          case Some(reg) =>
            submissionService.submitTopUpToDES(reg.registrationID, statusUpdate) map (_ => Ok(Json.toJson(statusUpdate.crn)))
        } recoverWith {
          case invalid: ErrorRegistrationException =>
            Future.successful(Ok(s"Cannot process Incorporation Update for transaction ID '$transactionId' - ${invalid.getMessage}"))
          case _: MissingRegDocument =>
            Future.successful(Ok(s"No registration found for transaction id $transactionId"))
          case error: RegistrationInvalidStatus => registrationInvalidStatusHandler(error, transactionId)
          case mongo@(_: UpdateFailed | _: RetrieveFailed) =>
            logger.error(s"[processIncorporationData] Failed to process Incorporation Update for transaction ID '$transactionId' - database error. The update may have completed successfully downstream")
            Future.successful(InternalServerError)
          case e =>
            logger.error(s"[processIncorporationData] Error while processing Incorporation Data for registration with transactionId $transactionId - error: ${e.getMessage}")
            throw e
        }
      }
  }

  def registrationInvalidStatusHandler(regInvalidError: RegistrationInvalidStatus, transactionId: String)(implicit hc: HeaderCarrier): Future[Result] = {
    counterService.updateIncorpCount(regInvalidError.regId) map {
      case true => logger.info(s"[registrationInvalidStatusHandler] II has called with regID: ${regInvalidError.regId} more than ${counterService.maxIICounterCount} times")
        Ok(s" II has called with regID: ${regInvalidError.regId} more than ${counterService.maxIICounterCount} times")
      case false => logger.warn(s"[registrationInvalidStatusHandler] Warning cannot process Incorporation Update for transaction ID '$transactionId' - ${regInvalidError.getMessage}")
        InternalServerError
    } recover {
      case error: UpdateFailed =>
        logger.error(s"[registrationInvalidStatusHandler] returned a None when trying to upsert ${regInvalidError.regId} for transaction ID '$transactionId' - ${regInvalidError.getMessage}")
        InternalServerError
    }
  }

  def updateRegistrationWithEmpRef(ackref: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      implicit val fmt: Format[EmpRefNotification] = EmpRefNotification.format(APIValidation, crypto)
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
        authResult.ifAuthorised(regId, "getDocumentStatus") {
          registrationService.getStatus(regId) map { status =>
            Ok(Json.toJson(status))
          } recover {
            case missing: MissingRegDocument => NotFound(s"No PAYE registration document found for registration ID $regId")
          }
        }
      }
  }

  def deletePAYERegistrationIncorpRejected(regId: String): Action[AnyContent] = Action.async {
    implicit request =>
      registrationService.deletePAYERegistration(regId, PAYEStatus.invalid, PAYEStatus.draft) map {
        if (_) {
          logger.info(s"[deletePAYERegistrationIncorpRejected] Rejected Registration Document deleted for regId: $regId")
          Ok
        } else {
          logger.warn(s"[deletePAYERegistrationIncorpRejected] Registration Document not deleted when expected for regId: $regId")
          InternalServerError
        }
      } recover {
        case _: UnmatchedStatusException => PreconditionFailed
        case _: MissingRegDocument => NotFound
      }
  }

  def deletePAYERegistration(regId: String): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised(regId) { authResult =>
        authResult.ifAuthorised(regId, "deletePAYERegistration") {
          registrationService.deletePAYERegistration(regId, PAYEStatus.rejected) map { deleted =>
            if (deleted) Ok else InternalServerError
          } recover {
            case _: UnmatchedStatusException => PreconditionFailed
          }
        }
      }
  }
}
