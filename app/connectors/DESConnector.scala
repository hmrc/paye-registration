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

package connectors

import javax.inject.{Inject, Singleton}

import audit.FailedDesSubmissionEvent
import config.{MicroserviceAuditConnector, WSHttp}
import models.incorporation.IncorpStatusUpdate
import models.submission.{DESSubmission, TopUpDESSubmission}
import play.api.Logger
import play.api.libs.json.Writes
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.ServicesConfig
import utils.PAYEFeatureSwitches

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.Authorization

@Singleton
class DESConnector extends DESConnect with ServicesConfig {
  val featureSwitch = PAYEFeatureSwitches
  lazy val desUrl = getConfString("des-service.url", "")
  lazy val desURI = getConfString("des-service.uri", "")
  lazy val desTopUpURI = getConfString("des-service.top-up-uri", "")
  lazy val desStubUrl = baseUrl("des-stub")
  lazy val desStubURI = getConfString("des-stub.uri", "")
  lazy val desStubTopUpURI = getConfString("des-stub.top-up-uri", "")
  lazy val urlHeaderEnvironment: String = getConfString("des-service.environment", throw new Exception("could not find config value for des-service.environment"))
  lazy val urlHeaderAuthorization: String = s"Bearer ${getConfString("des-service.authorization-token",
    throw new Exception("could not find config value for des-service.authorization-token"))}"
  val http = WSHttp
  val auditConnector = MicroserviceAuditConnector
}

trait DESConnect extends HttpErrorFunctions {

  val desUrl: String
  val desURI: String
  val desTopUpURI: String

  val desStubUrl: String
  val desStubURI: String
  val desStubTopUpURI: String

  def http: CorePost
  val featureSwitch: PAYEFeatureSwitches

  val urlHeaderEnvironment: String
  val urlHeaderAuthorization: String

  val auditConnector : AuditConnector

  private[connectors] def customDESRead(http: String, url: String, response: HttpResponse): HttpResponse = {
    response.status match {
      case 409 =>
        Logger.warn("[DESConnect] - [customDESRead] Received 409 from DES - converting to 200")
        HttpResponse(200, Some(response.json), response.allHeaders, Option(response.body))
      case 499 =>
        throw new Upstream4xxResponse(upstreamResponseMessage(http, url, response.status, response.body), 499, reportAs = 502, response.allHeaders)
      case status if is4xx(status) =>
        if(status == 400) {Logger.error("PAYE_-_400_response_returned_from_DES")} //used in alerting - DO NOT CHANGE ERROR TEXT
        throw new Upstream4xxResponse(upstreamResponseMessage(http, url, status, response.body), status, reportAs = 400, response.allHeaders)
      case _ => handleResponse(http, url)(response)
    }
  }

  implicit val httpRds = new HttpReads[HttpResponse] {
    def read(http: String, url: String, res: HttpResponse) = customDESRead(http, url, res)
  }

  def submitToDES(submission: DESSubmission, regId: String, incorpStatusUpdate: Option[IncorpStatusUpdate])(implicit hc: HeaderCarrier): Future[HttpResponse] = {

    val url = useDESStubFeature match {
      case true  => s"$desStubUrl/$desStubURI"
      case false => s"$desUrl/$desURI"
    }

    Logger.info(s"[DESConnector] - [submitToDES]: Submission to DES for regId: $regId, ackRef ${submission.acknowledgementReference} and txId: ${incorpStatusUpdate.map(_.transactionId)}")
    payePOST[DESSubmission, HttpResponse](url, submission) map { resp =>
      Logger.info(s"[DESConnector] - [submitToDES]: DES responded with ${resp.status} for regId: $regId and txId: ${incorpStatusUpdate.map(_.transactionId)}")
      resp
    } recoverWith {
      case e: Upstream4xxResponse =>
        val event = new FailedDesSubmissionEvent(regId, submission)
        auditConnector.sendExtendedEvent(event)
        Future.failed(e)
    }
  }

  def submitTopUpToDES(submission: TopUpDESSubmission, regId: String, txId: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url = useDESStubFeature match {
      case true  => s"$desStubUrl/$desStubTopUpURI"
      case false => s"$desUrl/$desTopUpURI"
    }

    Logger.info(s"[DESConnector] - [submitTopUpToDES]: Top Up to DES for regId: $regId, ackRef: ${submission.acknowledgementReference} and txId: $txId")
    payePOST[TopUpDESSubmission, HttpResponse](url, submission) map { resp =>
      Logger.info(s"[DESConnector] - [submitTopUpToDES]: DES responded with ${resp.status} for regId: $regId and txId: $txId")
      resp
    }
  }

  @inline
  private def payePOST[I, O](url: String, body: I, headers: Seq[(String, String)] = Seq.empty)(implicit wts: Writes[I], rds: HttpReads[O], hc: HeaderCarrier, ec: ExecutionContext) =
    http.POST[I, O](url, body, headers)(wts = wts, rds = rds, hc = createHeaderCarrier(hc), ec = ec)

  private[connectors] def useDESStubFeature: Boolean = !featureSwitch.desService.enabled

  private def createHeaderCarrier(headerCarrier: HeaderCarrier): HeaderCarrier = {
    headerCarrier.
      withExtraHeaders("Environment" -> urlHeaderEnvironment).
      copy(authorization = Some(Authorization(urlHeaderAuthorization)))
  }
}
