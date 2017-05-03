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

import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, JsSuccess, Json}
import uk.gov.hmrc.play.test.UnitSpec

class DirectorSpec extends UnitSpec with JsonFormatValidation {
  "Name" should {
    val tstJson = Json.parse(
      s"""{
         |  "forename":"Thierry'",
         |  "other_forenames":"Dominique;",
         |  "surname":"Henr-01y",
         |  "title":"Sir"
         |}""".stripMargin)

    val tstModel = Name(
      forename = Some("Thierry'"),
      otherForenames = Some("Dominique;"),
      surname = Some("Henr-01y"),
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

    "read successfully" when {
      "all fields are at maximum length" in {
        val maxName = List.fill(100)('a').mkString
        val maxTitle = List.fill(20)('a').mkString
        val nameJson = Json.parse(
          s"""{
             |  "forename":"$maxName",
             |  "other_forenames":"$maxName",
             |  "surname":"$maxName",
             |  "title":"$maxTitle"
             |}""".stripMargin)
        val fullNameModel = Name(
          forename = Some(maxName),
          otherForenames = Some(maxName),
          surname = Some(maxName),
          title = Some(maxTitle)
        )
        Json.fromJson[Name](nameJson) shouldBe JsSuccess(fullNameModel)
      }
    }

    "fail to read" when {
      def testNameJson(name: String = "Name", title: String = "Title") = Json.parse(
        s"""{
           |  "forename":"$name",
           |  "other_forenames":"Middle",
           |  "surname":"Last",
           |  "title":"$title"
           |}""".stripMargin)
      "name is too long" in {
        val result = Json.fromJson[Name](testNameJson(name = List.fill(101)('a').mkString))
        shouldHaveErrors(result, JsPath() \ "forename", Seq(ValidationError("error.pattern")))
      }
      "name is invalid" in {
        val result = Json.fromJson[Name](testNameJson(name = "Name$$"))
        shouldHaveErrors(result, JsPath() \ "forename", Seq(ValidationError("error.pattern")))
      }
      "title is too long" in {
        val result = Json.fromJson[Name](testNameJson(title = List.fill(21)('a').mkString))
        shouldHaveErrors(result, JsPath() \ "title", Seq(ValidationError("error.pattern")))
      }
      "title is invalid" in {
        val result = Json.fromJson[Name](testNameJson(title = "Title1"))
        shouldHaveErrors(result, JsPath() \ "title", Seq(ValidationError("error.pattern")))
      }
    }
  }

  "Director" should {
    val tstJson = Json.parse(
      s"""
         |{
         |  "nino":"SR098765C",
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

    val tstModel = Director(tstName, Some("SR098765C"))

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

    "fail from Json with invalid nino" in {
      def json(nino: String) = Json.parse(
        s"""
           |{
           |  "nino":"$nino",
           |  "director": {
           |    "forename":"Thierry",
           |    "other_forenames":"Dominique",
           |    "surname":"Henry",
           |    "title":"Sir"
           |  }
           |}
      """.stripMargin)

      def result(nino: String) = Json.fromJson[Director](json(nino))

      shouldHaveErrors(result("BG098765B"), JsPath() \ "nino", Seq(ValidationError("error.pattern")))

      shouldHaveErrors(result("AD098765D"), JsPath() \ "nino", Seq(ValidationError("error.pattern")))

      shouldHaveErrors(result("CV098765C"), JsPath() \ "nino", Seq(ValidationError("error.pattern")))

      shouldHaveErrors(result("SR098765Z"), JsPath() \ "nino", Seq(ValidationError("error.pattern")))

      shouldHaveErrors(result("SR098765Z"), JsPath() \ "nino", Seq(ValidationError("error.pattern")))

      shouldHaveErrors(result("SR 09 87 65 C"), JsPath() \ "nino", Seq(ValidationError("error.pattern")))
    }
  }
}
