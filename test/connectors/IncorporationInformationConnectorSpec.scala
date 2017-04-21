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

import helpers.PAYERegSpec
import models.incorporation.IncorpStatusUpdate
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.Helpers.{ACCEPTED, INTERNAL_SERVER_ERROR, OK}
import uk.gov.hmrc.play.http.ws.WSHttp
import org.mockito.Mockito.when
import org.mockito.ArgumentMatchers
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

class IncorporationInformationConnectorSpec extends PAYERegSpec {

  val mockHttp = mock[WSHttp]

  val testJson = Json.parse(
    """
      |{
      |   "SCRSIncorpStatus" : {
      |     "IncorpSubscriptionKey" : {
      |       "subscriber" : "SCRS",
      |       "discriminator" : "paye",
      |       "transactionId" : "testTxId"
      |     },
      |     "SCRSIncorpSubscription" : {
      |       "callbackUrl" : "/test/call-back"
      |     },
      |     "IncorpStatusEvent" : {
      |       "status" : "accepted",
      |       "crn" : "OC123456",
      |       "timestamp" : "2016-01-01T12:00:00Z"
      |     }
      |   }
      |}
    """.stripMargin
  )

  implicit val hc = HeaderCarrier()

  class Setup {
    val testConnector = new IncorporationInformationConnect {
      override val incorporationInformationUri: String = "/test/uri"
      override val http: WSHttp = mockHttp
    }
  }

  "constructIncorporationInfoUri" should {
    "construct the the II uri" when {
      "given a txId, regime and subscriber" in new Setup {
        val result = testConnector.constructIncorporationInfoUri("testTxId", "paye", "SCRS")
        result shouldBe "/incorporation-information/subscribe/testTxId/regime/paye/subscriber/SCRS"
      }
    }
  }

  "checkStatus" should {
    "return an IncorpStatusUpdate" when {
      "interest has been registered and there is already information about the given incorporation" in new Setup {
        val testResponse = new HttpResponse {
          override def status: Int = OK
          override def json: JsValue = testJson
        }

        when(mockHttp.POST[JsObject, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(testResponse))

        val result = await(testConnector.getIncorporationUpdate("testTxId", "paye", "SCRS", "/test/call-back"))
        result shouldBe Some(Json.fromJson[IncorpStatusUpdate](testJson).get)
      }
    }

    "return None" when {
      "interested has been registered for a given incorporation" in new Setup {
        val testResponse = new HttpResponse {
          override def status: Int = ACCEPTED
        }

        when(mockHttp.POST[JsObject, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(testResponse))

        val result = await(testConnector.getIncorporationUpdate("testTxId", "paye", "SCRS", "/test/call-back"))
        result shouldBe None
      }
    }

    "throw an IncorporationInformationResponseException" when {
      "when there was a problem on II" in new Setup {
        val testResponse = new HttpResponse {
          override def status: Int = INTERNAL_SERVER_ERROR
        }

        when(mockHttp.POST[JsObject, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(testResponse))

        val result = intercept[IncorporationInformationResponseException](await(testConnector.getIncorporationUpdate("testTxId", "paye", "SCRS", "/test/call-back")))
        result.getMessage shouldBe s"Calling II on /incorporation-information/subscribe/testTxId/regime/paye/subscriber/SCRS returned a 500"
      }
    }
  }
}
