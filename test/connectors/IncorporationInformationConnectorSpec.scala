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
import models.incorporation.IncorpStatusUpdate
import models.validation.APIValidation
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import scala.concurrent.Future

class IncorporationInformationConnectorSpec extends PAYERegSpec {

  val mockHttp = mock[HttpClient]

  val testIncorpStatusJson = Json.parse(
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

  val testIncorpStatus = testIncorpStatusJson.as[IncorpStatusUpdate](IncorpStatusUpdate.reads(APIValidation))

  implicit val hc = HeaderCarrier()
  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

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
        result mustBe "/incorporation-information/subscribe/testTxId/regime/paye/subscriber/SCRS"
      }
    }
  }

  "checkStatus" should {
    "return an IncorpStatusUpdate" when {
      "interest has been registered and there is already information about the given incorporation" in new Setup {

        when(mockHttp.POST[JsObject, Option[IncorpStatusUpdate]](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(Some(testIncorpStatus)))

        val result = await(Connector.getIncorporationUpdate("testTxId", "paye", "SCRS", "testRegId"))
        result mustBe Some(testIncorpStatus)
      }
    }

    "return None" when {
      "interested has been registered for a given incorporation" in new Setup {

        when(mockHttp.POST[JsObject, Option[IncorpStatusUpdate]](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(None))

        val result = await(Connector.getIncorporationUpdate("testTxId", "paye", "SCRS", "testRegId"))
        result mustBe None
      }
    }

    "throw an IncorporationInformationResponseException" when {
      "when there was a problem on II" in new Setup {

        when(mockHttp.POST[JsObject, Option[IncorpStatusUpdate]](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.failed(new IncorporationInformationResponseException("bang")))

        intercept[IncorporationInformationResponseException](await(Connector.getIncorporationUpdate("testTxId", "paye", "SCRS", "testRegId")))
      }
    }
  }

  "getIncorporationDate" should {
    "return Some(LocalDate)" in new Setup {

      val date = LocalDate.of(2015, 12, 25)

      when(mockHttp.GET[Option[LocalDate]](any(), any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Some(date)))

      val res = await(Connector.getIncorporationDate("testTxId"))
      res mustBe Some(date)
    }

    "return an exception if the future fails" in new Setup {

      when(mockHttp.GET[Option[LocalDate]](any(), any(), any())(any(), any(), any()))
        .thenReturn(Future.failed(new IncorporationInformationResponseException("bang")))

      intercept[IncorporationInformationResponseException](await(Connector.getIncorporationDate("testTxId")))
    }
  }
}
