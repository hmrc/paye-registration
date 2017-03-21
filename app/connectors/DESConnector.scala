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
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost, HttpResponse}
import utils.{PAYEFeatureSwitch, PAYEFeatureSwitches}

import scala.concurrent.Future

@Singleton
class DESConnector @Inject()(injFeatureSwitch: PAYEFeatureSwitch) extends DESConnect with ServicesConfig {
  val featureSwitch = injFeatureSwitch
  lazy val desUrl = useDESStubFeature match {
    case true => baseUrl("des-stub")
    case false => baseUrl("des-stub") //TODO: set the production DES settings
  }
  lazy val desURI = useDESStubFeature match {
    case true => getConfString("des-stub.uri", "")
    case false => getConfString("des-stub.uri", "") //TODO: set the production DES settings
  }
  val http = WSHttp
}

trait DESConnect {

  val desUrl: String
  val desURI: String
  def http: HttpPost
  val featureSwitch: PAYEFeatureSwitches

  def submitToDES(submission: DESSubmission)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    http.POST[DESSubmission, HttpResponse](s"$desUrl/$desURI", submission)
  }

  private[connectors] def useDESStubFeature: Boolean = featureSwitch.desStubFeature.enabled

}
