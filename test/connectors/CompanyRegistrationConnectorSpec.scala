/*
 * Copyright 2019 HM Revenue & Customs
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

import helpers.PAYERegSpec
import mocks.WSHTTPMock
import models.external.BusinessProfile
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier, HttpResponse, NotFoundException}
import uk.gov.hmrc.play.http.ws.WSHttp

import scala.concurrent.Future

class CompanyRegistrationConnectorSpec extends PAYERegSpec with WSHTTPMock {

  val testJson = Json.parse(
    """
      |{
      | "testKey" : "testValue"
      |}
    """.stripMargin
  )

  implicit val hc = HeaderCarrier()

  class Setup {
    val connector = new CompanyRegistrationConnect {
      override val compRegUrl: String = "/testUrl"
      override val http: WSHttp = mockWSHttp
    }
  }

  "fetchCompanyRegistrationDocument" should {
    "return an OK with JSON body" when {
      "given a valid regId" in new Setup {
        val okResponse = new HttpResponse {
          override def status: Int = OK
          override def json: JsValue = testJson
        }

        mockHttpGet[HttpResponse]("testUrl", okResponse)

        val result = await(connector.fetchCompanyRegistrationDocument("testRegId", Some("testTxId")))
        result shouldBe okResponse
      }
    }

    "throw a not found exception" when {
      "the reg document cant be found" in new Setup {
        when(mockWSHttp.GET[BusinessProfile](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.failed(new NotFoundException("Bad request")))

        intercept[NotFoundException](await(connector.fetchCompanyRegistrationDocument("testRegId", Some("testTxId"))))
      }
    }

    "throw a forbidden exception" when {
      "the request is not authorised" in new Setup {
        when(mockWSHttp.GET[BusinessProfile](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.failed(new ForbiddenException("Forbidden")))

        intercept[ForbiddenException](await(connector.fetchCompanyRegistrationDocument("testRegId", Some("testTxId"))))
      }
    }

    "throw an unchecked exception" when {
      "an unexpected response code was returned" in new Setup {
        when(mockWSHttp.GET[BusinessProfile](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.failed(new RuntimeException("Runtime Exception")))

        intercept[Throwable](await(connector.fetchCompanyRegistrationDocument("testRegId", Some("testTxId"))))
      }
    }
  }
}
