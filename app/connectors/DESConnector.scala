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

package connectors

import javax.inject.{Inject, Singleton}

import config.WSHttp
import models.submission.{TopUpDESSubmission, DESSubmission}
import play.api.Logger
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import utils.{PAYEFeatureSwitch, PAYEFeatureSwitches}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class DESConnector @Inject()(injFeatureSwitch: PAYEFeatureSwitch) extends DESConnect with ServicesConfig {
  val featureSwitch = injFeatureSwitch
  lazy val desUrl = getConfString("des-service.url", "")
  lazy val desURI = getConfString("des-service.uri", "")
  lazy val desTopUpURI = getConfString("des-service.top-up-uri", "")
  lazy val desStubUrl = baseUrl("des-stub")
  lazy val desStubURI = getConfString("des-stub.uri", "")
  lazy val desStubTopUpURI = getConfString("des-stub.top-up-uri", "")
  val http = WSHttp
}

trait DESConnect extends HttpErrorFunctions {

  val desUrl: String
  val desURI: String
  val desTopUpURI: String

  val desStubUrl: String
  val desStubURI: String
  val desStubTopUpURI: String

  def http: HttpPost
  val featureSwitch: PAYEFeatureSwitches

  private[connectors] def customDESRead(http: String, url: String, response: HttpResponse): HttpResponse = {
    response.status match {
      case 409 =>
        Logger.warn("[DESConnect] - [customDESRead] Received 409 from DES - converting to 200")
        HttpResponse(200, Some(response.json), response.allHeaders, Option(response.body))
      case 499 =>
        throw new Upstream4xxResponse(upstreamResponseMessage(http, url, response.status, response.body), 499, reportAs = 502, response.allHeaders)
      case status if is4xx(status) =>
        if(status == 400) {Logger.error("PAYE - 400 response returned from DES")} //used in alerting - DO NOT CHANGE ERROR TEXT
        throw new Upstream4xxResponse(upstreamResponseMessage(http, url, status, response.body), status, reportAs = 400, response.allHeaders)
      case _ => handleResponse(http, url)(response)
    }
  }

  implicit val httpRds = new HttpReads[HttpResponse] {
    def read(http: String, url: String, res: HttpResponse) = customDESRead(http, url, res)
  }

  def submitToDES(submission: DESSubmission)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url = useDESStubFeature match {
      case true  => s"$desStubUrl/$desStubURI"
      case false => s"$desUrl/$desURI"
    }

    http.POST[DESSubmission, HttpResponse](url, submission) map { resp =>
      Logger.info(s"[DESConnector] - [submitToDES]: DES responded with ${resp.status}")
      resp
    }
  }

  def submitTopUpToDES(submission: TopUpDESSubmission)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url = useDESStubFeature match {
      case true  => s"$desStubUrl/$desStubTopUpURI"
      case false => s"$desUrl/$desTopUpURI"
    }

    http.POST[TopUpDESSubmission, HttpResponse](url, submission) map { resp =>
      Logger.info(s"[DESConnector] - [submitTopUpToDES]: DES responded with ${resp.status}")
      resp
    }
  }

  private[connectors] def useDESStubFeature: Boolean = !featureSwitch.desService.enabled

}
