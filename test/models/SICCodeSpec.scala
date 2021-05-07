/*
 * Copyright 2021 HM Revenue & Customs
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

import helpers.PAYERegSpec
import models.validation.APIValidation
import play.api.libs.json.{JsSuccess, Json}

class SICCodeSpec extends PAYERegSpec with JsonFormatValidation {
  "SICCode" should {
    val tstJsonFull = Json.parse(
      s"""
         |{
         |  "code":"1234",
         |  "description": "Construction company"
         |}
      """.stripMargin)

    val tstSICCodeFull = SICCode(
      code = Some("1234"),
      description = Some("Construction company")
    )

    val tstJson = Json.parse(
      s"""
         |{
         |  "description": "something"
         |}
      """.stripMargin)

    val tstSICCode = SICCode(
      code = None,
      description = Some("something")
    )

    "read from json with full data" in {
      Json.fromJson[SICCode](tstJsonFull)(SICCode.reads(APIValidation)) shouldBe JsSuccess(tstSICCodeFull)
    }

    "write to json with full data" in {
      Json.toJson[SICCode](tstSICCodeFull) shouldBe tstJsonFull
    }

    "read from json with none full data" in {
      Json.fromJson[SICCode](tstJson)(SICCode.reads(APIValidation)) shouldBe JsSuccess(tstSICCode)
    }

    "write to json with none full data" in {
      Json.toJson[SICCode](tstSICCode) shouldBe tstJson
    }
  }
}
