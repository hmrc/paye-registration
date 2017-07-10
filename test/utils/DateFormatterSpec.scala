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

package utils

import java.time.{ZoneOffset, ZonedDateTime}
import javax.inject.Inject

import helpers.DateHelper
import utils._
import models.JsonFormatValidation
import play.api.data.validation.ValidationError
import play.api.libs.json._
import uk.gov.hmrc.play.test.UnitSpec

class dateFormatterSpec extends UnitSpec with JsonFormatValidation  {
  object dateFormatter extends DateFormatter
    "dateTimeReadApi" should {
      "read jsValue String timestamp and return ZoneDateTime" in {
        val dt = ZonedDateTime.of(2000, 1, 20, 16, 1, 0, 0, ZoneOffset.UTC)
        val timeStamp = JsString("2000-01-20T16:01:00Z")
        val result = Json.fromJson[ZonedDateTime](timeStamp)(dateFormatter.dateTimeReadApi)
        result shouldBe JsSuccess(dt)
      }
      "read jsValue String timestamp from different zone and return ZoneDateTime" in {
        val dt = ZonedDateTime.of(2000, 1, 20, 16, 1, 0, 0, ZoneOffset.UTC)
        val timeStamp = JsString("2000-01-20T18:01:00+0200")
        val result = Json.fromJson[ZonedDateTime](timeStamp)(dateFormatter.dateTimeReadApi)
        result shouldBe JsSuccess(dt)
      }
      "return Jserror if the datetimeReadApi fails to convert timestamp" in {
        val timeStamp = JsString("2000-01-20T16:01:00ZAA")
        val result = Json.fromJson[ZonedDateTime](timeStamp)(dateFormatter.dateTimeReadApi)
        shouldHaveErrors(result, JsPath(), Seq(ValidationError("Text '2000-01-20T16:01:00ZAA' could not be parsed, unparsed text found at index 20")))
      }
    }

    "dateTimeWriteApi" should {
      "return a valid ZoneDateTime in timestamp format" in {
        val res = Json.toJson[ZonedDateTime](ZonedDateTime.of(2000, 1, 20, 16, 1, 0, 0, ZoneOffset.UTC))(dateFormatter.dateTimeWriteApi)
        res shouldBe JsString("2000-01-20T16:01:00Z")
      }
    }
    "dateTimeWriteMongo" should {
      "return a valid ZoneDateTime" in {
        val res = Json.toJson[ZonedDateTime](ZonedDateTime.of(2000, 1, 20, 16, 1, 0, 0, ZoneOffset.UTC))(dateFormatter.dateTimeWriteMongo)
        res shouldBe Json.obj("$date" -> "948384060000".toLong)
      }

    }
    "dateTimeReadMongo" should {
      "read epoch long value and convert to ZonedDateTime" in {
        val res = JsSuccess(ZonedDateTime.of(1985, 1, 20, 18, 1, 0, 0, ZoneOffset.UTC), JsPath().\("$date"))
        val json = Json.parse(
          """
         |{
         |"$date" : 475092060000
         |}
         |
       """.stripMargin)
      Json.
        fromJson[ZonedDateTime](json)(dateFormatter.dateTimeReadMongo) shouldBe res
    }
      "return JSFailure if date is a string" in {
      val json = Json.parse(
        """
                              |{
                              |"$date" : "475092060000A"
                              |}
                              |
       """.stripMargin)
      val result = Json.fromJson
        [ZonedDateTime](json)(dateFormatter.dateTimeReadMongo)
      shouldHaveErrors(result,

        JsPath(
        ).\("$date"), Seq(ValidationError("error.expected.jsnumber")))
    }
}
}