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
import models.submission.{DESSubmission, TopUpDESSubmission}
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import services.AuditService
import sttp.model.HeaderNames
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.StringContextOps
import utils.{Logging, SystemDate}

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HIPConnector @Inject()(val http: HttpClientV2, appConfig: AppConfig, val auditService: AuditService)
  extends BaseConnector with BaseHttpReads with HttpErrorFunctions with Logging with CorrelationGenerator {

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

  implicit val httpRds: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    def read(http: String, url: String, res: HttpResponse) = customHIPRead(http, url, res)
  }

  def submitToHIP(submission: DESSubmission, regId: String, incorpStatusUpdate: Option[IncorpStatusUpdate])
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {

    val url = s"${appConfig.hipUrl}/${appConfig.hipURI}"

    logger.info(s"[submitToHIP] Submission to HIP for regId: $regId, ackRef: ${submission.acknowledgementReference} and txId: ${incorpStatusUpdate.map(_.transactionId)}")
    payePOST(url, Json.toJson(submission)) map { resp =>
      logger.info(s"[submitToHIP] HIP responded with ${resp.status} for regId: $regId and txId: ${incorpStatusUpdate.map(_.transactionId)}")
      resp
    } recoverWith {
      case e: UpstreamErrorResponse if UpstreamErrorResponse.Upstream4xxResponse.unapply(e).isDefined =>
        logger.error(s"[submitToHIP] PAYE_400_HIP_SUBMISSION_FAILURE for regId: $regId")
        Future.failed(e)
    }
  }


  def submitTopUpToHIP(submission: TopUpDESSubmission, regId: String, txId: String)
                      (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {

    val url = s"${appConfig.hipUrl}/${appConfig.hipTopUpURI}"

    logger.info(s"[submitTopUpToHIP] Top Up to HIP for regId: $regId, ackRef: ${submission.acknowledgementReference} and txId: $txId")
    payePOST(url, Json.toJson(submission)) map { resp =>
      logger.info(s"[submitTopUpToHIP] HIP responded with ${resp.status} for regId: $regId and txId: $txId")
      resp
    } recoverWith {
      case e: UpstreamErrorResponse if UpstreamErrorResponse.Upstream4xxResponse.unapply(e).isDefined =>
        logger.error(s"[submitTopUpToHIP] PAYE_400_HIP_TOPUP_FAILURE for regId: $regId and txId: $txId")
        Future.failed(e)
    }
  }

  private def payePOST(uri: String, body: play.api.libs.json.JsValue)(implicit hc: HeaderCarrier, ec: ExecutionContext) = {

    val correlationId = addCorrelationId(hc).extraHeaders
      .map { case (key, value) => (key.toLowerCase, value) }
      .collectFirst { case ("correlationid", value) => value }
      .getOrElse(generateCorrelationId(hc.requestId))

    val authSecret: String = Base64.getEncoder
      .encodeToString(
        s"${appConfig.hipClientId}:${appConfig.hipClientSecret}"
          .getBytes(StandardCharsets.UTF_8)
      )

    val hipHeaders: Seq[(String, String)] = Seq(
      HeaderNames.Authorization -> s"Basic $authSecret",
      "X-Originating-System"    -> "SCRS",
      "correlationid"           -> correlationId,
      "X-Receipt-Date"          -> DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS)),
      "X-Transmitting-System"   -> "HIP"
    )

    http
      .post(url"$uri")(hc)
      .setHeader(hipHeaders: _*)
      .withBody(body)
      .execute[HttpResponse]
  }
}
