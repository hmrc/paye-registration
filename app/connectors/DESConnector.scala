/*
 * Copyright 2021 HM Revenue & Customs
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

import audit.FailedDesSubmissionEvent
import config.AppConfig
import models.incorporation.IncorpStatusUpdate
import models.submission.{DESSubmission, TopUpDESSubmission}
import play.api.Logger
import play.api.libs.json.Writes
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import utils.{PAYEFeatureSwitches, SystemDate, WorkingHoursGuard}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DESConnector @Inject()(val http: HttpClient, appConfig: AppConfig, val auditConnector: AuditConnector) extends HttpErrorFunctions with WorkingHoursGuard {
  val alertWorkingHours: String = appConfig.alertWorkingHours

  def currentDate = SystemDate.getSystemDate.toLocalDate
  def currentTime = SystemDate.getSystemDate.toLocalTime

  private[connectors] def customDESRead(http: String, url: String, response: HttpResponse): HttpResponse = {
    response.status match {
      case 409 =>
        Logger.warn("[DESConnect] - [customDESRead] Received 409 from DES - converting to 200")
        HttpResponse(200, Some(response.json), response.allHeaders, Option(response.body))
      case 429 =>
        throw new Upstream5xxResponse(upstreamResponseMessage(http, url, response.status, response.body), 429, reportAs = 503)
      case 499 =>
        throw new Upstream4xxResponse(upstreamResponseMessage(http, url, response.status, response.body), 499, reportAs = 502, response.allHeaders)
      case status if is4xx(status) =>
        throw new Upstream4xxResponse(upstreamResponseMessage(http, url, status, response.body), status, reportAs = 400, response.allHeaders)
      case _ => handleResponse(http, url)(response)
    }
  }

  private def logDes400PagerDuty(responce: Upstream4xxResponse, regId: String): Unit = if(responce.upstreamResponseCode == 400) {
    if(isInWorkingDaysAndHours) {
      Logger.error(s"PAYE_400_DES_SUBMISSION_FAILURE for regId: $regId with date: $currentDate and time: $currentTime") //used in alerting - DO NOT CHANGE ERROR TEXT
    } else {
      Logger.error(s"NON_PAGER_DUTY_LOG PAYE_400_DES_SUBMISSION_FAILURE for regId: $regId with date: $currentDate and time: $currentTime")
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

    Logger.info(s"[DESConnector] - [submitToDES]: Submission to DES for regId: $regId, ackRef ${submission.acknowledgementReference} and txId: ${incorpStatusUpdate.map(_.transactionId)}")
    payePOST[DESSubmission, HttpResponse](url, submission) map { resp =>
      Logger.info(s"[DESConnector] - [submitToDES]: DES responded with ${resp.status} for regId: $regId and txId: ${incorpStatusUpdate.map(_.transactionId)}")
      resp
    } recoverWith {
      case e: Upstream4xxResponse =>
        logDes400PagerDuty(e, regId)
        val event = new FailedDesSubmissionEvent(regId, submission)
        auditConnector.sendExtendedEvent(event)
        Future.failed(e)
    }
  }

  def submitTopUpToDES(submission: TopUpDESSubmission, regId: String, txId: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url = if (useDESStubFeature) {
      s"${appConfig.desStubUrl}/${appConfig.desStubTopUpURI}"
    } else {
      s"${appConfig.desUrl}/${appConfig.desTopUpURI}"
    }

    Logger.info(s"[DESConnector] - [submitTopUpToDES]: Top Up to DES for regId: $regId, ackRef: ${submission.acknowledgementReference} and txId: $txId")
    payePOST[TopUpDESSubmission, HttpResponse](url, submission) map { resp =>
      Logger.info(s"[DESConnector] - [submitTopUpToDES]: DES responded with ${resp.status} for regId: $regId and txId: $txId")
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
