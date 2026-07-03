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

package connectors

import audit.RegistrationAuditEventConstants.JOURNEY_ID
import config.AppConfig
import connectors.httpParsers.BaseHttpReads
import models.incorporation.IncorpStatusUpdate
import models.submission.{ApiSubmission, TopUpApiSubmission}
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import services.AuditService
import sttp.model.HeaderNames
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import utils.{Logging, SystemDate, WorkingHoursGuard}

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDate, LocalTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HIPConnector @Inject()(val http: HttpClientV2, appConfig: AppConfig, val auditService: AuditService)
  extends BaseConnector with BaseHttpReads with HttpErrorFunctions with Logging with CorrelationGenerator with WorkingHoursGuard {

  val alertWorkingHours: String = appConfig.alertWorkingHours
  def currentDate: LocalDate = SystemDate.getSystemDate.toLocalDate
  def currentTime: LocalTime = SystemDate.getSystemDate.toLocalTime

  implicit val httpRds: HttpReads[HttpResponse] =
    (http: String, url: String, res: HttpResponse) => customHIPRead(http, url, res)

  def submitRegistration(submission: ApiSubmission, regId: String, incorpStatusUpdate: Option[IncorpStatusUpdate])
                        (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {

    val url = s"${appConfig.hipBaseUrl}/RESTAdapter/business-registration/PAYE"
    payePOST(url, Json.toJson(submission)) map { resp =>
      logger.info(s"[submitRegistration] HIP responded with ${resp.status} for regId: $regId and txId: ${incorpStatusUpdate.map(_.transactionId)}")
      resp
    } recoverWith {
      case e: UpstreamErrorResponse if UpstreamErrorResponse.Upstream4xxResponse.unapply(e).isDefined =>
        logHip400PagerDuty(e, regId)
        auditService.sendEvent("payeRegistrationSubmissionFailure", Json.obj("submission" -> submission, JOURNEY_ID -> regId))
        Future.failed(e)
    }
  }

  def submitIncorporation(submission: TopUpApiSubmission, regId: String, txId: String)
                         (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {

    val url = s"${appConfig.hipBaseUrl}/RESTAdapter/business-incorporation/PAYE"
    payePOST(url, Json.toJson(submission)) map { resp =>
      logger.info(s"[submitIncorporation] HIP responded with ${resp.status} for regId: $regId and txId: $txId")
      resp
    } recoverWith {
      case e: UpstreamErrorResponse if UpstreamErrorResponse.Upstream4xxResponse.unapply(e).isDefined =>
        logHip400PagerDuty(e, regId)
        Future.failed(e)
    }
  }

  private def payePOST(uri: String, body: play.api.libs.json.JsValue)(implicit hc: HeaderCarrier, ec: ExecutionContext) = {

    val correlationId = addCorrelationId(hc).extraHeaders
      .map { case (key, value) => (key.toLowerCase, value) }
      .collectFirst { case ("correlationid", value) => value }
      .getOrElse(generateCorrelationId(hc.requestId))

    val hipHeaders: Seq[(String, String)] = Seq(
      HeaderNames.Authorization -> s"Basic ${appConfig.hipAuthToken}",
      "X-Originating-System"    -> "SCRS",
      "correlationid"           -> correlationId,
      "X-Receipt-Date"          -> DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS)),
      "X-Transmitting-System"   -> "HIP"
    )
    val hcWithoutAuth = hc.copy(authorization = None)

    logger.info(s"[HipConnector] Calling endpoint: $uri with Correlation ID: $correlationId")
    http
      .post(url"$uri")(hcWithoutAuth)
      .setHeader(hipHeaders: _*)
      .withBody(body)
      .execute[HttpResponse]
  }

  private[connectors] def customHIPRead(http: String, url: String, response: HttpResponse): HttpResponse = {
    response.status match {
      case 409 =>
        logger.warn("[customHIPRead] Received 409 from HIP - converting to 200")
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

  private def logHip400PagerDuty(response: UpstreamErrorResponse, regId: String): Unit = if (response.statusCode == 400) {
    val alert400Failure = "PAYE_400_HIP_SUBMISSION_FAILURE"   //DON'T CHANGE - triggers a custom alert in alert-config.
    val nonAlert        = "NON_PAGER_DUTY_LOG"                //DON'T CHANGE - stops a PagerDuty being triggered.

    if (isInWorkingDaysAndHours) {
      logger.error(s"[logHip400PagerDuty] $alert400Failure for regId: $regId with date: $currentDate and time: $currentTime") //used in alerting - DO NOT CHANGE ERROR TEXT
    } else {
      logger.error(s"[logHip400PagerDuty] $nonAlert $alert400Failure for regId: $regId with date: $currentDate and time: $currentTime")
    }
  }
}
