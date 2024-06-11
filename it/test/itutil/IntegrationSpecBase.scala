/*
 * Copyright 2023 HM Revenue & Customs
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

package itutil

import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.HeaderNames
import utils.{FeatureSwitch, PAYEFeatureSwitches}

trait IntegrationSpecBase extends PlaySpec
  with GuiceOneServerPerSuite with ScalaFutures with IntegrationPatience
  with WiremockHelper with BeforeAndAfterEach with BeforeAndAfterAll {

  def setupFeatures(desService: Boolean = false,
                    removeStaleDocumentsJob: Boolean = false,
                    metricsJob: Boolean = false
                   ) = {
    def enableFeature(fs: FeatureSwitch, enabled: Boolean) = {
      enabled match {
        case true => FeatureSwitch.enable(fs)
        case _ => FeatureSwitch.disable(fs)
      }
    }

    enableFeature(PAYEFeatureSwitches.desService, desService)
    enableFeature(PAYEFeatureSwitches.removeStaleDocuments, removeStaleDocumentsJob)
    enableFeature(PAYEFeatureSwitches.graphiteMetrics, metricsJob)
  }

  def client(path: String) = ws
    .url(s"http://localhost:$port/paye-registration$path")
    .withHttpHeaders(
      HeaderNames.AUTHORIZATION -> "AuthToken",
      "X-Session-ID" -> "session-12345"
    )
    .withFollowRedirects(false)

  override def beforeEach() = {
    resetWiremock()
  }

  override def beforeAll() = {
    super.beforeAll()
    startWiremock()
  }

  override def afterAll() = {
    stopWiremock()
    super.afterAll()
  }
}
