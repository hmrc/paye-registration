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

import models.JsonFormatValidation
import play.api.data.validation.ValidationError
import play.api.libs.json._
import uk.gov.hmrc.play.test.UnitSpec
import play.api.libs.functional.syntax._
/**
  * Created by chill on 06/07/2017.
  */
class DateFormatterSpec extends UnitSpec with DateFormatter with JsonFormatValidation{

"dateTimeReadApi" should {
  "read jsValue String timestamp and return ZoneDateTime" in {
    val dt = ZonedDateTime.of(2000,1,20,16,1,0,0,ZoneOffset.UTC)
    val timeStamp = JsString("2000-01-20T16:01:00Z")
    val result = Json.fromJson[ZonedDateTime](timeStamp)(dateTimeReadApi)
    result shouldBe JsSuccess(dt)
  }
}


  "dateTimeWriteMongo" should {
    "return a valid ZoneDateTime" in {
      val res = Json.toJson[ZonedDateTime](ZonedDateTime.of(2000,1,20,16,1,0,0,ZoneOffset.UTC))(dateTimeWriteMongo)
      res shouldBe Json.obj("$date" -> "948384060000".toLong)
    }

  }
  "dateTimeReadMongo" should {
    "read epoch long value and convert to ZonedDateTime" in {
      val res = JsSuccess(ZonedDateTime.of(1985,1,20,18,1,0,0,ZoneOffset.UTC), JsPath().\("$date"))
     val json = Json.parse(
       """
         |{
         |"$date" : 475092060000
         |}
         |
       """.stripMargin)
      Json.fromJson[ZonedDateTime](json)(dateTimeReadMongo) shouldBe res
    }
    "return JSFailure" in {
      val json = Json.parse("""
                              |{
                              |"$date" : "475092060000A"
                              |}
                              |
       """.stripMargin)
      val result = Json.fromJson[ZonedDateTime](json)(dateTimeReadMongo)
      shouldHaveErrors(result,JsPath().\("$date"), Seq(ValidationError("error.expected.jsnumber")))
    }
  }
}
