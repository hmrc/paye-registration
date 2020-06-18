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

package models.incorporation

import java.time.LocalDate

import enums.IncorporationStatus
import models.JsonFormatValidation
import models.validation.APIValidation
import play.api.libs.json.{JsPath, JsSuccess, JsValue, Json, JsonValidationError}
import uk.gov.hmrc.play.test.UnitSpec

class IncorpStatusUpdateSpec extends UnitSpec with JsonFormatValidation {

  private def statusUpdateFromJson(json: JsValue) = Json.fromJson[IncorpStatusUpdate](json)(IncorpStatusUpdate.reads(APIValidation))

  "Creating a IncorpStatusUpdate model from Json" should {
    "succeed" when {
      "provided with full json" in {
        val json = Json.parse(
          s"""
             |{
             |  "SCRSIncorpStatus": {
             |    "IncorpSubscriptionKey" : {
             |      "subscriber" : "SCRS",
             |      "discriminator" : "PAYE",
             |      "transactionId" : "NNASD9789F"
             |    },
             |    "SCRSIncorpSubscription" : {
             |      "callbackUrl" : "scrs-incorporation-update-listener.service/incorp-updates/incorp-status-update"
             |    },
             |    "IncorpStatusEvent": {
             |      "status": "accepted",
             |      "crn":"1",
             |      "incorporationDate":"2000-12-12",
             |      "timestamp" : ${Json.toJson(LocalDate.of(2017, 12, 21))}
             |    }
             |  }
             |}
        """.stripMargin)

        val tstIncorpStatusUpdate = IncorpStatusUpdate(
          transactionId = "NNASD9789F",
          status = IncorporationStatus.accepted,
          crn = Some("1"),
          incorporationDate = Some(LocalDate.of(2000, 12, 12)),
          description = None,
          timestamp = LocalDate.of(2017, 12, 21)
        )

        statusUpdateFromJson(json) shouldBe JsSuccess(tstIncorpStatusUpdate)
      }
      "there is no CRN in an update with rejected status" in {
        val json = Json.parse(
          s"""
             |{
             |  "SCRSIncorpStatus": {
             |    "IncorpSubscriptionKey" : {
             |      "subscriber" : "SCRS",
             |      "discriminator" : "PAYE",
             |      "transactionId" : "NNASD9789F"
             |    },
             |    "SCRSIncorpSubscription" : {
             |      "callbackUrl" : "scrs-incorporation-update-listener.service/incorp-updates/incorp-status-update"
             |    },
             |    "IncorpStatusEvent": {
             |      "status": "rejected",
             |      "incorporationDate":"2000-12-12",
             |      "timestamp" : ${Json.toJson(LocalDate.of(2017, 12, 21))}
             |    }
             |  }
             |}
        """.stripMargin)

        val tstIncorpStatusUpdate = IncorpStatusUpdate(
          transactionId = "NNASD9789F",
          status = IncorporationStatus.rejected,
          crn = None,
          incorporationDate = Some(LocalDate.of(2000, 12, 12)),
          description = None,
          timestamp = LocalDate.of(2017, 12, 21)
        )

        statusUpdateFromJson(json) shouldBe JsSuccess(tstIncorpStatusUpdate)
      }
    }

    "fail" when {

      "status is not defined" in {
        val json = Json.parse(
          s"""
             |{
             |  "SCRSIncorpStatus": {
             |    "IncorpSubscriptionKey" : {
             |      "subscriber" : "SCRS",
             |      "discriminator" : "PAYE",
             |      "transactionId" : "NNASD9789F"
             |    },
             |    "SCRSIncorpSubscription" : {
             |      "callbackUrl" : "scrs-incorporation-update-listener.service/incorp-updates/incorp-status-update"
             |    },
             |    "IncorpStatusEvent": {
             |      "crn":"1",
             |      "incorporationDate":"2000-12-12",
             |      "timestamp" : ${Json.toJson(LocalDate.of(2017, 12, 21))}
             |    }
             |  }
             |}
        """.stripMargin)

        val result = statusUpdateFromJson(json)
        shouldHaveErrors(result, JsPath() \\ "IncorpStatusEvent" \ "status", Seq(JsonValidationError("error.path.missing")))
      }

      "accepted status and no CRN" in {
        val json = Json.parse(
          s"""
             |{
             |  "SCRSIncorpStatus": {
             |    "IncorpSubscriptionKey" : {
             |      "subscriber" : "SCRS",
             |      "discriminator" : "PAYE",
             |      "transactionId" : "NNASD9789F"
             |    },
             |    "SCRSIncorpSubscription" : {
             |      "callbackUrl" : "scrs-incorporation-update-listener.service/incorp-updates/incorp-status-update"
             |    },
             |    "IncorpStatusEvent": {
             |      "status": "accepted",
             |      "incorporationDate":"2000-12-12",
             |      "timestamp" : ${Json.toJson(LocalDate.of(2017, 12, 21))}
             |    }
             |  }
             |}
        """.stripMargin)

        val result = statusUpdateFromJson(json)
        shouldHaveErrors(result, JsPath(), Seq(JsonValidationError("no CRN defined when expected")))
      }
    }
  }
}
