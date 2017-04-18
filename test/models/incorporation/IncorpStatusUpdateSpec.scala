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

package models.incorporation

import java.time.LocalDate

import models.JsonFormatValidation
import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, JsSuccess, Json}
import uk.gov.hmrc.play.test.UnitSpec

class IncorpStatusUpdateSpec extends UnitSpec with JsonFormatValidation {
  "Creating a IncorpStatusUpdate model from Json" should {
    "complete successfully from full Json" in {
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
           |      "timestamp" : "2017-12-21T10:13:09.429Z"
           |    }
           |  }
           |}
        """.stripMargin)

      val tstIncorpStatusUpdate = IncorpStatusUpdate(
        transactionId = "NNASD9789F",
        status = "accepted",
        crn = Some("1"),
        incorporationDate = Some(LocalDate.of(2000, 12, 12)),
        description = None,
        timestamp = "2017-12-21T10:13:09.429Z"
      )

      Json.fromJson[IncorpStatusUpdate](json) shouldBe JsSuccess(tstIncorpStatusUpdate)
    }

    "fail from Json without status" in {
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
           |      "timestamp" : "2017-12-21T10:13:09.429Z"
           |    }
           |  }
           |}
        """.stripMargin)

      val result = Json.fromJson[IncorpStatusUpdate](json)
      shouldHaveErrors(result, JsPath() \\ "IncorpStatusEvent" \ "status", Seq(ValidationError("error.path.missing")))
    }
  }
}
