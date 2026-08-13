/*
 * Copyright 2026 HM Revenue & Customs
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
import fixtures.SubmissionFixture
import helpers.PAYERegSpec
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.{Assertion, BeforeAndAfter}
import play.api.libs.json.Json
import play.api.test.Helpers._
import services.AuditService
import sttp.model.HeaderNames
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

class HIPConnectorSpec extends PAYERegSpec with BeforeAndAfter with SubmissionFixture {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  val mockHttpClientV2: HttpClientV2 = mock[HttpClientV2]
  val mockAuditService: AuditService = mock[AuditService]

  before {
    reset(mockHttpClientV2)
  }

  trait Setup {

    val serviceUrl = "http://hipURL"
    val busReqUrl = url"$serviceUrl/etmp/RESTAdapter/business-registration/PAYE"
    val busIncUrl = url"$serviceUrl/etmp/RESTAdapter/business-incorporation/PAYE"

    val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]
    val  mockAppConfig: AppConfig = new AppConfig(mock[ServicesConfig]) {
      override lazy val hipBaseUrl: String = serviceUrl
      override lazy val hipClientId = "testId"
      override lazy val hipClientSecret = "testSecret"
      override lazy val alertWorkingHours = "00:00:00_23:59:59"
    }

    val connector = new HIPConnector(mockHttpClientV2, mockAppConfig, mockAuditService)

    def mockHttpPost[I](url: URL, payload: I, httpResponse: HttpResponse): Unit = {
      when(mockHttpClientV2.post(eqTo(url))(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(eqTo(payload))(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[HttpResponse](any(), any())).thenReturn(Future.successful(httpResponse))
    }

    def mockHttpPostFailed[I](url: URL, payload: I, httpResponse: UpstreamErrorResponse): Unit = {
      when(mockHttpClientV2.post(eqTo(url))(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(eqTo(payload))(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[HttpResponse](any(), any())).thenReturn(Future.failed(httpResponse))
    }

    def verifyHipHeaders(): Assertion = {
      val hipHeadersSet = Set(HeaderNames.Authorization, "X-Originating-System", "correlationid", "X-Receipt-Date", "X-Transmitting-System")
      val hipHeaderFixed = Set("HIP", "SCRS")

      val captor = ArgumentCaptor.forClass(classOf[Seq[(String, String)]])
      verify(mockRequestBuilder, times(1)).setHeader(captor.capture(): _*)
      captor.getValue.map(_._1).toSet mustBe hipHeadersSet
      captor.getValue.map(_._2).toSet.intersect(hipHeaderFixed) mustBe hipHeaderFixed
    }

    reset(mockHttpClientV2)
    reset(mockRequestBuilder)
  }

  "HipConnector" when {

    "performing submitRegistration with a Partial Submission Model" should {
      val submissionJson = Json.toJson(validPartialDESSubmissionModel)

      "successfully POST to HIP" in new Setup {
        mockHttpPost(busReqUrl, submissionJson, HttpResponse(200, ""))
        await(connector.submitRegistration(validPartialDESSubmissionModel, "testRegId", Some(incorpStatusUpdate)))
          .status mustBe 200
        verifyHipHeaders()
      }

      "throw exception if a 400 is encountered" in new Setup {
        mockHttpPostFailed(busReqUrl, submissionJson, UpstreamErrorResponse("OOPS", 400, 400))
        intercept[UpstreamErrorResponse](
          await(connector.submitRegistration(validPartialDESSubmissionModel, "testRegId", Some(incorpStatusUpdate)))
        )
        verifyHipHeaders()
      }
    }

    "performing submitIncorporation with a Top Up Submission Model" should {
      val submissionJson = Json.toJson(validTopUpDESSubmissionModel)

      "successfully POST to HIP" in new Setup {
        mockHttpPost(busIncUrl, submissionJson, HttpResponse(200, ""))
        await(connector.submitIncorporation(validTopUpDESSubmissionModel, "testRegId", incorpStatusUpdate.transactionId))
          .status mustBe 200
        verifyHipHeaders()
      }

      "throw exception if a 400 is encountered" in new Setup {
        mockHttpPostFailed(busIncUrl, submissionJson, UpstreamErrorResponse("OOPS", 400, 400))
        intercept[UpstreamErrorResponse](
          await(connector.submitIncorporation(validTopUpDESSubmissionModel, "testRegId", incorpStatusUpdate.transactionId))
        )
        verifyHipHeaders()
      }
    }
  }

  "customHIPRead" should {
    "convert a 409 to a 200" in new Setup {
      val response = HttpResponse(409, "")
      connector.customHIPRead("POST", "testUrl", response).status mustBe 200
    }

    "throw UpstreamErrorResponse for 429" in new Setup {
      intercept[UpstreamErrorResponse] {
        connector.customHIPRead("POST", "testUrl", HttpResponse(429, ""))
      }.reportAs mustBe 503
    }

    "throw UpstreamErrorResponse for 499" in new Setup {
      intercept[UpstreamErrorResponse] {
        connector.customHIPRead("POST", "testUrl", HttpResponse(499, ""))
      }.reportAs mustBe 502
    }

    "throw UpstreamErrorResponse for other 4xx" in new Setup {
      intercept[UpstreamErrorResponse] {
        connector.customHIPRead("POST", "testUrl", HttpResponse(400, ""))
      }.reportAs mustBe 400
    }

    "return the response for a 200" in new Setup {
      val response = HttpResponse(200, "")
      connector.customHIPRead("POST", "testUrl", response).status mustBe 200
    }

    "return the response for a 202" in new Setup {
      val response = HttpResponse(202, "")
      connector.customHIPRead("POST", "testUrl", response).status mustBe 202
    }

    "throw UpstreamErrorResponse for a 500" in new Setup {
      intercept[UpstreamErrorResponse] {
        connector.customHIPRead("POST", "testUrl", HttpResponse(500, ""))
      }
    }

    "throw UpstreamErrorResponse for a 503" in new Setup {
      intercept[UpstreamErrorResponse] {
        connector.customHIPRead("POST", "testUrl", HttpResponse(503, ""))
      }
    }
  }
}