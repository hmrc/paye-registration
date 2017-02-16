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

package models

import play.api.libs.json.{JsSuccess, Json}
import uk.gov.hmrc.play.test.UnitSpec

class DirectorSpec extends UnitSpec with JsonFormatValidation {
  "Name" should {
    val tstJson = Json.parse(
      s"""{
         |  "forename":"Thierry",
         |  "other_forenames":"Dominique",
         |  "surname":"Henry",
         |  "title":"Sir"
         |}""".stripMargin)

    val tstModel = Name(
      forename = Some("Thierry"),
      otherForenames = Some("Dominique"),
      surname = Some("Henry"),
      title = Some("Sir")
    )

    "read from json with full data" in {
      Json.fromJson[Name](tstJson) shouldBe JsSuccess(tstModel)
    }

    "write to json with full data" in {
      Json.toJson[Name](tstModel) shouldBe tstJson
    }

    val tstEmptyJson = Json.parse(s"""{}""".stripMargin)
    val tstEmptyModel = Name(None, None, None, None)
    "read from json with empty data" in {
      Json.fromJson[Name](tstEmptyJson) shouldBe JsSuccess(tstEmptyModel)
    }

    "write to json with empty data" in {
      Json.toJson[Name](tstEmptyModel) shouldBe tstEmptyJson
    }
  }

  "Director" should {
    val tstJson = Json.parse(
      s"""
         |{
         |  "nino":"AA098765Z",
         |  "director": {
         |    "forename":"Thierry",
         |    "other_forenames":"Dominique",
         |    "surname":"Henry",
         |    "title":"Sir"
         |  }
         |}
      """.stripMargin)

    val tstName = Name(
      forename = Some("Thierry"),
      otherForenames = Some("Dominique"),
      surname = Some("Henry"),
      title = Some("Sir")
    )

    val tstModel = Director(tstName, Some("AA098765Z"))

    "read from json with full data" in {
      Json.fromJson[Director](tstJson) shouldBe JsSuccess(tstModel)
    }

    "write to json with full data" in {
      Json.toJson[Director](tstModel) shouldBe tstJson
    }

    val tstEmptyJson = Json.parse(s"""{"director":{}}""".stripMargin)
    val tstEmptyModel = Director(Name(None, None, None, None), None)
    "read from json with empty data" in {
      Json.fromJson[Director](tstEmptyJson) shouldBe JsSuccess(tstEmptyModel)
    }

    "write to json with empty data" in {
      Json.toJson[Director](tstEmptyModel) shouldBe tstEmptyJson
    }
  }
}
