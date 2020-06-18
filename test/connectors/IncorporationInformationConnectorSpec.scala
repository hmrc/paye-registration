/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.LocalDate

import config.AppConfig
import helpers.PAYERegSpec
import models.incorporation.IncorpStatusUpdate
import models.validation.APIValidation
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{reset, when}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.ws.WSHttp

import scala.concurrent.Future

class IncorporationInformationConnectorSpec extends PAYERegSpec {

  val mockHttp = mock[HttpClient]

  val testJson = Json.parse(
    s"""
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
      |       "timestamp" : ${Json.toJson(LocalDate.of(2016, 1, 1))}
      |     }
      |   }
      |}
    """.stripMargin
  )

  implicit val hc = HeaderCarrier()

  class Setup {
    object MockAppConfig extends AppConfig(mock[ServicesConfig]) {
      override lazy val incorporationInformationUri: String = "/test/uri"
      override lazy val payeRegUri: String = "/test-reg/"
    }
    object Connector extends IncorporationInformationConnector(mockHttp, MockAppConfig)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockHttp)
  }

  "constructIncorporationInfoUri" should {
    "construct the the II uri" when {
      "given a txId, regime and subscriber" in new Setup {
        val result = Connector.constructIncorporationInfoUri("testTxId", "paye", "SCRS")
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

        when(mockHttp.POST[JsObject, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(testResponse))

        val result = await(Connector.getIncorporationUpdate("testTxId", "paye", "SCRS", "testRegId"))
        result shouldBe Some(Json.fromJson[IncorpStatusUpdate](testJson)(IncorpStatusUpdate.reads(APIValidation)).get)
      }
    }

    "return None" when {
      "interested has been registered for a given incorporation" in new Setup {
        val testResponse = new HttpResponse {
          override def status: Int = ACCEPTED
        }

        when(mockHttp.POST[JsObject, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(testResponse))

        val result = await(Connector.getIncorporationUpdate("testTxId", "paye", "SCRS", "testRegId"))
        result shouldBe None
      }
    }

    "throw an IncorporationInformationResponseException" when {
      "when there was a problem on II" in new Setup {
        val testResponse = new HttpResponse {
          override def status: Int = INTERNAL_SERVER_ERROR
        }

        when(mockHttp.POST[JsObject, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(testResponse))

        val result = intercept[IncorporationInformationResponseException](await(Connector.getIncorporationUpdate("testTxId", "paye", "SCRS", "testRegId")))
        result.getMessage shouldBe s"Calling II on /incorporation-information/subscribe/testTxId/regime/paye/subscriber/SCRS returned a 500"
      }
    }
  }

  "getIncorporationDate" should {
    "return Some(LocalDate)" in new Setup {
      val testJson = Json.parse(
        """
          |{
          |   "crn": "fooBarWizz",
          |   "incorporationDate": "2015-12-25"
          |}
        """.stripMargin
      )

      val testResponse = new HttpResponse {
        override def status: Int = OK
        override def json: JsValue = testJson
      }
      when(mockHttp.GET[HttpResponse](ArgumentMatchers.any())(ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any()))
        .thenReturn(Future.successful(testResponse))

      val res = await(Connector.getIncorporationDate("testTxId"))
      res shouldBe Some(LocalDate.of(2015,12,25))
    }


    "return None when local date cannot be parsed" in new Setup {
      val testJson = Json.parse(
        """
          |{
          |   "crn": "fooBarWizz",
          |   "incorporationDate": "2015-12-"
          |}
        """.stripMargin
      )
      val testResponse = new HttpResponse {
        override def status: Int = OK
        override def json: JsValue = testJson
      }
      when(mockHttp.GET[HttpResponse](ArgumentMatchers.any())(ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any()))
        .thenReturn(Future.successful(testResponse))
      val res = await(Connector.getIncorporationDate("testTxId"))
      res shouldBe None
    }
    "return None when element does not exist" in new Setup {
      val testJson = Json.parse(
        """
          |{
          |   "crn": "fooBarWizz"
          |}
        """.stripMargin
      )
      val testResponse = new HttpResponse {
        override def status: Int = OK
        override def json: JsValue = testJson
      }
      when(mockHttp.GET[HttpResponse](ArgumentMatchers.any())(ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any()))
        .thenReturn(Future.successful(testResponse))
      val res = await(Connector.getIncorporationDate("testTxId"))
      res shouldBe None
    }
    "return None when response is 204" in new Setup {
      val testResponse = new HttpResponse {
        override def status: Int = NO_CONTENT
      }
      when(mockHttp.GET[HttpResponse](ArgumentMatchers.any())(ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any()))
        .thenReturn(Future.successful(testResponse))
      val res = await(Connector.getIncorporationDate("testTxId"))
      res shouldBe None
    }

    "return an exception if the response code from II is not expected" in new Setup {
      val testResponse = new HttpResponse {
        override def status: Int = ACCEPTED
      }
      when(mockHttp.GET[HttpResponse](ArgumentMatchers.any())(ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any()))
        .thenReturn(Future.successful(testResponse))

      an[IncorporationInformationResponseException] shouldBe thrownBy(await(Connector.getIncorporationDate("testTxId")))
    }
  }
}
