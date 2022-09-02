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

package connectors

import config.AppConfig
import helpers.PAYERegSpec
import mocks.HTTPMock
import models.external.BusinessProfile
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier, HttpResponse, NotFoundException}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.Future

class CompanyRegistrationConnectorSpec extends PAYERegSpec with HTTPMock {

  val testJson: JsValue = Json.parse(
    """
      |{
      | "testKey" : "testValue"
      |}
    """.stripMargin
  )

  implicit val hc: HeaderCarrier = HeaderCarrier()

  class Setup {

    object MockAppConfig extends AppConfig(mock[ServicesConfig]) {
      override lazy val compRegUrl: String = "/testUrl"
    }

    object Connector extends CompanyRegistrationConnector(mockHttp, MockAppConfig)

  }

  "fetchCompanyRegistrationDocument" should {
    "return an OK with JSON body" when {
      "given a valid regId" in new Setup {
        val okResponse: HttpResponse = HttpResponse.apply(OK, testJson.toString())

        mockHttpGet[HttpResponse]("testUrl", okResponse)

        val result: HttpResponse = await(Connector.fetchCompanyRegistrationDocument("testRegId", Some("testTxId")))
        result mustBe okResponse
      }
    }

    "throw a not found exception" when {
      "the reg document cant be found" in new Setup {
        when(mockHttp.GET[BusinessProfile](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.failed(new NotFoundException("Bad request")))

        intercept[NotFoundException](await(Connector.fetchCompanyRegistrationDocument("testRegId", Some("testTxId"))))
      }
    }

    "throw a forbidden exception" when {
      "the request is not authorised" in new Setup {
        when(mockHttp.GET[BusinessProfile](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.failed(new ForbiddenException("Forbidden")))

        intercept[ForbiddenException](await(Connector.fetchCompanyRegistrationDocument("testRegId", Some("testTxId"))))
      }
    }

    "throw an unchecked exception" when {
      "an unexpected response code was returned" in new Setup {
        when(mockHttp.GET[BusinessProfile](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.failed(new RuntimeException("Runtime Exception")))

        intercept[Throwable](await(Connector.fetchCompanyRegistrationDocument("testRegId", Some("testTxId"))))
      }
    }
  }
}
