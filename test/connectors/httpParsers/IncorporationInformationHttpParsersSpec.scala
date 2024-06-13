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

package connectors.httpParsers

import ch.qos.logback.classic.Level
import connectors.IncorporationInformationResponseException
import helpers.PAYERegSpec
import models.incorporation.IncorpStatusUpdate
import play.api.http.Status.{ACCEPTED, INTERNAL_SERVER_ERROR, NO_CONTENT, OK}
import play.api.libs.json.{JsResultException, Json}
import uk.gov.hmrc.http.HttpResponse
import utils.LogCapturingHelper

import java.time.LocalDate

class IncorporationInformationHttpParsersSpec extends PAYERegSpec with LogCapturingHelper {

  val regId = "reg12345"
  val txId = "tx12345"
  val ctUtr = "1234567890"

  "IncorporationInformationHttpParsers" when {

    "calling .incorporationDateHttpReads(txId: String)" when {

      val incorpDate = LocalDate.of(2015,12,25)
      val testIncorpDateJson = Json.parse(
        s"""
           |{
           |   "crn": "fooBarWizz",
           |   "incorporationDate": "${incorpDate.toString}"
           |}
        """.stripMargin
      )

      val rds = IncorporationInformationHttpParsers.incorporationDateHttpReads(txId)

      "response is OK and JSON is valid" must {

        "return Some(date)" in {

          val response = HttpResponse(OK, json = testIncorpDateJson, Map())
          rds.read("", "", response) mustBe Some(incorpDate)
        }
      }

      "response is OK and IncorpDate missing" must {

        "return None" in {

          val response = HttpResponse(OK, json = Json.obj(), Map())
          rds.read("", "", response) mustBe None
        }
      }

      "response is NO_CONTENT" must {

        "return None" in {

          val response = HttpResponse(NO_CONTENT, "")
          rds.read("", "", response) mustBe None
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Upstream Error response and log an error" in {

          withCaptureOfLoggingFrom(IncorporationInformationHttpParsers.logger) { logs =>
            intercept[IncorporationInformationResponseException](rds.read("", "/foo/bar", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[IncorporationInformationHttpParsers][incorporationDateHttpReads] Calling url: '/foo/bar' returned unexpected status: '$INTERNAL_SERVER_ERROR' for txId: '$txId'")
          }
        }
      }
    }

    "calling .incorpStatusUpdateHttpReads(regId: String, txId: String)" when {

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
      val testIncorpStatusModel = testIncorpStatusJson.as[IncorpStatusUpdate]

      val rds = IncorporationInformationHttpParsers.incorpStatusUpdateHttpReads(regId, txId)

      "response is OK and JSON is valid" must {

        "return Some(date)" in {

          val response = HttpResponse(OK, json = testIncorpStatusJson, Map())
          rds.read("", "", response) mustBe Some(testIncorpStatusModel)
        }
      }

      "response is OK but JSON is malformed" must {

        "throw JsResultException" in {

          val response = HttpResponse(OK, json = Json.obj(), Map())
          withCaptureOfLoggingFrom(IncorporationInformationHttpParsers.logger) { logs =>
            intercept[JsResultException](rds.read("", "", response))
            logs.containsMsg(Level.ERROR, s"[IncorporationInformationHttpParsers][incorpStatusUpdateHttpReads] JSON returned could not be parsed to models.incorporation.IncorpStatusUpdate model for regId: '$regId' and txId: '$txId'")
          }
        }
      }

      "response is ACCEPTED" must {

        "return None" in {

          val response = HttpResponse(ACCEPTED, "")
          rds.read("", "", response) mustBe None
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Upstream Error response and log an error" in {

          withCaptureOfLoggingFrom(IncorporationInformationHttpParsers.logger) { logs =>
            intercept[IncorporationInformationResponseException](rds.read("", "/foo/bar", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[IncorporationInformationHttpParsers][incorpStatusUpdateHttpReads] Calling url: '/foo/bar' returned unexpected status: '$INTERNAL_SERVER_ERROR' for regId: '$regId' and txId: '$txId'")
          }
        }
      }
    }
  }
}
