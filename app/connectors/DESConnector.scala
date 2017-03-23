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
import models.submission.DESSubmission
import play.api.Logger
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost, HttpResponse}
import utils.{PAYEFeatureSwitch, PAYEFeatureSwitches}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class DESConnector @Inject()(injFeatureSwitch: PAYEFeatureSwitch) extends DESConnect with ServicesConfig {
  val featureSwitch = injFeatureSwitch
  lazy val desUrl = baseUrl("des-service")
  lazy val desURI = getConfString("des-service.uri", "")
  lazy val desStubUrl = baseUrl("des-stub")
  lazy val desStubURI = getConfString("des-service.uri", "")
  val http = WSHttp
}

trait DESConnect {

  val desUrl: String
  val desURI: String

  val desStubUrl: String
  val desStubURI: String

  def http: HttpPost
  val featureSwitch: PAYEFeatureSwitches

  def submitToDES(submission: DESSubmission)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    if(useDESStubFeature) {
      http.POST[DESSubmission, HttpResponse](s"$desStubUrl/$desStubURI", submission) map { resp =>
        Logger.info(s"[DESConnector] - [submitToDES]: DES responded with ${resp.status}")
        resp
      }
    } else {
      http.POST[DESSubmission, HttpResponse](s"$desUrl/$desURI", submission) map { resp =>
        Logger.info(s"[DESConnector] - [submitToDES]: DES responded with ${resp.status}")
        resp
      }
    }
  }

  private[connectors] def useDESStubFeature: Boolean = !featureSwitch.desService.enabled

}
