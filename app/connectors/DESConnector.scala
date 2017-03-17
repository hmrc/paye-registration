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
import models.submission.PartialDESSubmission
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost, HttpResponse}

@Singleton
class DESConnector extends DESConnect with ServicesConfig {
  lazy val desUrl = baseUrl("des-stub")
  lazy val desURI = getConfString("des-stub.uri", "")
  val http = WSHttp
}

trait DESConnect {

  val desUrl: String
  val desURI: String
  def http: HttpPost

  def submitToDES(submission: PartialDESSubmission)(implicit hc: HeaderCarrier) = {
    http.POST[PartialDESSubmission, HttpResponse](s"$desUrl/$desURI", submission)
  }

}
