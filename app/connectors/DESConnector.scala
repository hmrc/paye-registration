/*
 * Copyright 2022 HM Revenue & Customs
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

package connectors

import audit.RegistrationAuditEventConstants.JOURNEY_ID
import config.AppConfig
import models.incorporation.IncorpStatusUpdate
import models.submission.{DESSubmission, TopUpDESSubmission}
import utils.Logging
import play.api.libs.json.{Json, Writes}
import services.AuditService
import uk.gov.hmrc.http._
import utils.{PAYEFeatureSwitches, SystemDate, WorkingHoursGuard}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DESConnector @Inject()(val http: HttpClient, appConfig: AppConfig, val auditService: AuditService) extends HttpErrorFunctions with WorkingHoursGuard with Logging {
  val alertWorkingHours: String = appConfig.alertWorkingHours

  def currentDate = SystemDate.getSystemDate.toLocalDate

  def currentTime = SystemDate.getSystemDate.toLocalTime

  private[connectors] def customDESRead(http: String, url: String, response: HttpResponse): HttpResponse = {
    response.status match {
      case 409 =>
        logger.warn("[customDESRead] Received 409 from DES - converting to 200")
        HttpResponse(200, response.body, response.headers)
      case 429 =>
        throw UpstreamErrorResponse(upstreamResponseMessage(http, url, response.status, response.body), 429, reportAs = 503, response.headers)
      case 499 =>
        throw UpstreamErrorResponse(upstreamResponseMessage(http, url, response.status, response.body), 499, reportAs = 502, response.headers)
      case status if is4xx(status) =>
        throw UpstreamErrorResponse(upstreamResponseMessage(http, url, status, response.body), status, reportAs = 400, response.headers)
      case _ =>
        handleResponseEither(http, url)(response).fold(e => throw e, identity)
    }
  }

  private def logDes400PagerDuty(response: UpstreamErrorResponse, regId: String): Unit = if (response.statusCode == 400) {
    if (isInWorkingDaysAndHours) {
      logger.error(s"[logDes400PagerDuty] PAYE_400_DES_SUBMISSION_FAILURE for regId: $regId with date: $currentDate and time: $currentTime") //used in alerting - DO NOT CHANGE ERROR TEXT
    } else {
      logger.error(s"[logDes400PagerDuty] NON_PAGER_DUTY_LOG PAYE_400_DES_SUBMISSION_FAILURE for regId: $regId with date: $currentDate and time: $currentTime")
    }
  }

  implicit val httpRds = new HttpReads[HttpResponse] {
    def read(http: String, url: String, res: HttpResponse) = customDESRead(http, url, res)
  }

  def submitToDES(submission: DESSubmission, regId: String, incorpStatusUpdate: Option[IncorpStatusUpdate])(implicit hc: HeaderCarrier): Future[HttpResponse] = {

    val url = if (useDESStubFeature) {
      s"${appConfig.desStubUrl}/${appConfig.desStubURI}"
    } else {
      s"${appConfig.desUrl}/${appConfig.desURI}"
    }

    logger.info(s"[submitToDES] Submission to DES for regId: $regId, ackRef ${submission.acknowledgementReference} and txId: ${incorpStatusUpdate.map(_.transactionId)}")
    payePOST[DESSubmission, HttpResponse](url, submission) map { resp =>
      logger.info(s"[submitToDES] DES responded with ${resp.status} for regId: $regId and txId: ${incorpStatusUpdate.map(_.transactionId)}")
      resp
    } recoverWith {
      case e: Upstream4xxResponse =>
        logDes400PagerDuty(e, regId)
        auditService.sendEvent("payeRegistrationSubmissionFailure", Json.obj("submission" -> submission, JOURNEY_ID -> regId))
        Future.failed(e)
    }
  }

  def submitTopUpToDES(submission: TopUpDESSubmission, regId: String, txId: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url = if (useDESStubFeature) {
      s"${appConfig.desStubUrl}/${appConfig.desStubTopUpURI}"
    } else {
      s"${appConfig.desUrl}/${appConfig.desTopUpURI}"
    }

    logger.info(s"[submitTopUpToDES] Top Up to DES for regId: $regId, ackRef: ${submission.acknowledgementReference} and txId: $txId")
    payePOST[TopUpDESSubmission, HttpResponse](url, submission) map { resp =>
      logger.info(s"[submitTopUpToDES] DES responded with ${resp.status} for regId: $regId and txId: $txId")
      resp
    } recoverWith {
      case e: Upstream4xxResponse =>
        logDes400PagerDuty(e, regId)
        Future.failed(e)
    }
  }

  private def payePOST[I, O](url: String, body: I, headers: Seq[(String, String)] = createExtraHeaders)(implicit wts: Writes[I], rds: HttpReads[O], hc: HeaderCarrier, ec: ExecutionContext) =
    http.POST[I, O](url, body, headers)(wts = wts, rds = rds, hc = hc, ec = ec)

  private[connectors] def useDESStubFeature: Boolean = !PAYEFeatureSwitches.desService.enabled

  private val createExtraHeaders = Seq(
    "Authorization" -> appConfig.desUrlHeaderAuthorization,
    "Environment" -> appConfig.desUrlHeaderEnvironment
  )
}
