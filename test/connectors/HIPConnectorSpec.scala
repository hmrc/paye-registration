/*
 * Copyright 2024 HM Revenue & Customs
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
import models.submission.{DESSubmission, TopUpDESSubmission}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import play.api.test.Helpers._
import services.AuditService
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

class HIPConnectorSpec extends PAYERegSpec with BeforeAndAfter with SubmissionFixture {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val mockHttpClientV2: HttpClientV2 = mock[HttpClientV2]
  val mockAuditService: AuditService = mock[AuditService]

  before {
    reset(mockHttpClientV2)
  }

  class Setup {
    val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

    object MockAppConfig extends AppConfig(mock[ServicesConfig]) {
      override lazy val hipURI = "RESTAdapter/business-registration/PAYE"
      override lazy val hipTopUpURI = "RESTAdapter/business-incorporation/PAYE"
      override lazy val hipUrl = "http://hipURL"
      override lazy val hipClientId = "testClientId"
      override lazy val hipClientSecret = "testClientSecret"
    }

    object Connector extends HIPConnector(mockHttpClientV2, MockAppConfig, mockAuditService)

    def mockHttpPost(thenReturn: HttpResponse): Unit = {
      when(mockHttpClientV2.post(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.setHeader(ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(thenReturn))
    }

    def mockHttpPostFailed(exception: Exception): Unit = {
      when(mockHttpClientV2.post(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.setHeader(ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.failed(exception))
    }
  }

  "submitToHIP with a Partial DES Submission Model" should {
    "successfully POST to HIP" in new Setup {
      mockHttpPost(HttpResponse(200, ""))
      await(Connector.submitToHIP(validPartialDESSubmissionModel, "testRegId", Some(incorpStatusUpdate))).status mustBe 200
    }

    "throw exception if a 400 is encountered" in new Setup {
      mockHttpPostFailed(UpstreamErrorResponse("OOPS", 400, 400))
      intercept[UpstreamErrorResponse](await(Connector.submitToHIP(validPartialDESSubmissionModel, "testRegId", Some(incorpStatusUpdate))))
    }
  }

  "submitTopUpToHIP with a Top Up DES Submission Model" should {
    "successfully POST to HIP" in new Setup {
      mockHttpPost(HttpResponse(200, ""))
      await(Connector.submitTopUpToHIP(validTopUpDESSubmissionModel, "testRegId", incorpStatusUpdate.transactionId)).status mustBe 200
    }

    "throw exception if a 400 is encountered" in new Setup {
      mockHttpPostFailed(UpstreamErrorResponse("OOPS", 400, 400))
      intercept[UpstreamErrorResponse](await(Connector.submitTopUpToHIP(validTopUpDESSubmissionModel, "testRegId", incorpStatusUpdate.transactionId)))
    }
  }

  "customHIPRead" should {
    "convert a 409 to a 200" in new Setup {
      val response = HttpResponse(409, "")
      Connector.customHIPRead("POST", "testUrl", response).status mustBe 200
    }

    "throw UpstreamErrorResponse for 429" in new Setup {
      intercept[UpstreamErrorResponse] {
        Connector.customHIPRead("POST", "testUrl", HttpResponse(429, ""))
      }.reportAs mustBe 503
    }

    "throw UpstreamErrorResponse for 499" in new Setup {
      intercept[UpstreamErrorResponse] {
        Connector.customHIPRead("POST", "testUrl", HttpResponse(499, ""))
      }.reportAs mustBe 502
    }

    "throw UpstreamErrorResponse for other 4xx" in new Setup {
      intercept[UpstreamErrorResponse] {
        Connector.customHIPRead("POST", "testUrl", HttpResponse(400, ""))
      }.reportAs mustBe 400
    }

    "return the response for a 200" in new Setup {
      val response = HttpResponse(200, "")
      Connector.customHIPRead("POST", "testUrl", response).status mustBe 200
    }

    "return the response for a 202" in new Setup {
      val response = HttpResponse(202, "")
      Connector.customHIPRead("POST", "testUrl", response).status mustBe 202
    }

    "throw UpstreamErrorResponse for a 500" in new Setup {
      intercept[UpstreamErrorResponse] {
        Connector.customHIPRead("POST", "testUrl", HttpResponse(500, ""))
      }
    }

    "throw UpstreamErrorResponse for a 503" in new Setup {
      intercept[UpstreamErrorResponse] {
        Connector.customHIPRead("POST", "testUrl", HttpResponse(503, ""))
      }
    }
  }
}