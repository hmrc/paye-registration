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

package itutil

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatestplus.play.BaseOneServerPerSuite
import play.api.libs.ws.WSClient

object WiremockHelper {
  val wiremockPort = 11111
  val wiremockHost = "localhost"
  val url = s"http://$wiremockHost:$wiremockPort"
}

trait WiremockHelper {
  self: BaseOneServerPerSuite =>

  import WiremockHelper._

  lazy val ws = app.injector.instanceOf(classOf[WSClient])

  val wmConfig = wireMockConfig().port(wiremockPort)
  val wireMockServer = new WireMockServer(wmConfig)

  def startWiremock() = {
    wireMockServer.start()
    WireMock.configureFor(wiremockHost, wiremockPort)
  }

  def stopWiremock() = wireMockServer.stop()

  def resetWiremock() = WireMock.reset()

  def stubGet(url: String, status: Integer, body: String) =
    stubFor(get(urlMatching(url))
      .willReturn(
        aResponse().
          withStatus(status).
          withBody(body)
      )
    )

  def stubPost(url: String, status: Integer, responseBody: String) =
    stubFor(post(urlMatching(url))
      .willReturn(
        aResponse().
          withStatus(status).
          withBody(responseBody)
      )
    )


  val userId = "/auth/oid/1234567890"
  def setupSimpleAuthMocks() = {
    stubFor(post(urlMatching("/write/audit/merged"))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody("""{"x":2}""")
      )
    )

    stubFor(post(urlMatching("/auth/authorise")).willReturn(aResponse().withStatus(200).withBody("""{"internalId": "Int-xxx"}""")))

    stubFor(get(urlMatching("/auth/userDetails"))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody("""
                      |{
                      |   "name":"xxx",
                      |   "email":"xxx",
                      |   "affinityGroup":"xxx",
                      |   "authProviderId":"123456789",
                      |   "authProviderType":"xxx"
                      |}
                   """.stripMargin)
      )
    )
  }

  def setupAuthMocksToReturn(res: String) = {
    stubFor(post(urlMatching("/write/audit"))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody("""{"x":2}""")
      )
    )

    stubFor(post(urlMatching("/auth/authorise")).willReturn(aResponse().withStatus(200).withBody(res)))

    stubFor(get(urlMatching("/auth/userDetails"))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody("""
                     |{
                     |   "name":"xxx",
                     |   "email":"xxx",
                     |   "affinityGroup":"xxx",
                     |   "authProviderId":"123456789",
                     |   "authProviderType":"xxx"
                     |}
                   """.stripMargin)
      )
    )
  }
}
