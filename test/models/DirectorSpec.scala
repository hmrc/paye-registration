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

package models

import helpers.PAYERegSpec
import models.validation.APIValidation
import play.api.libs.json.{JsPath, JsSuccess, Json, JsonValidationError}

class DirectorSpec extends PAYERegSpec with JsonFormatValidation {
  "Name" should {
    val tstJson = Json.parse(
      s"""{
         |  "forename":"Thierry'",
         |  "other_forenames":"Dominique;",
         |  "surname":"Henr-01y",
         |  "title":"Dr-Sir"
         |}""".stripMargin)

    val tstModel = Name(
      forename = Some("Thierry'"),
      otherForenames = Some("Dominique;"),
      surname = Some("Henr-01y"),
      title = Some("Dr-Sir")
    )

    "read from json with full data" in {
      Json.fromJson[Name](tstJson)(Name.format(APIValidation)) mustBe JsSuccess(tstModel)
    }

    "write to json with full data" in {
      Json.toJson[Name](tstModel)(Name.format(APIValidation)) mustBe tstJson
    }

    val tstEmptyJson = Json.parse(s"""{}""".stripMargin)
    val tstEmptyModel = Name(None, None, None, None)
    "read from json with empty data" in {
      Json.fromJson[Name](tstEmptyJson)(Name.format(APIValidation)) mustBe JsSuccess(tstEmptyModel)
    }

    "write to json with empty data" in {
      Json.toJson[Name](tstEmptyModel)(Name.format(APIValidation)) mustBe tstEmptyJson
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
        Json.fromJson[Name](nameJson)(Name.format(APIValidation)) mustBe JsSuccess(fullNameModel)
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
        val result = Json.fromJson[Name](testNameJson(name = List.fill(101)('a').mkString))(Name.format(APIValidation))
        shouldHaveErrors(result, JsPath() \ "forename", Seq(JsonValidationError("error.pattern")))
      }
      "name is invalid" in {
        val result = Json.fromJson[Name](testNameJson(name = "Name$$"))(Name.format(APIValidation))
        shouldHaveErrors(result, JsPath() \ "forename", Seq(JsonValidationError("error.pattern")))
      }
      "title is too long" in {
        val result = Json.fromJson[Name](testNameJson(title = List.fill(21)('a').mkString))(Name.format(APIValidation))
        shouldHaveErrors(result, JsPath() \ "title", Seq(JsonValidationError("error.pattern")))
      }
      "title is invalid" in {
        val result = Json.fromJson[Name](testNameJson(title = "Dr.Title1"))(Name.format(APIValidation))
        shouldHaveErrors(result, JsPath() \ "title", Seq(JsonValidationError("error.pattern")))
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
      Json.fromJson[Director](tstJson)(Director.format(APIValidation)) mustBe JsSuccess(tstModel)
    }

    "write to json with full data" in {
      Json.toJson[Director](tstModel)(Director.format(APIValidation)) mustBe tstJson
    }

    val tstEmptyJson = Json.parse(s"""{"director":{}}""".stripMargin)
    val tstEmptyModel = Director(Name(None, None, None, None), None)
    "read from json with empty data" in {
      Json.fromJson[Director](tstEmptyJson)(Director.format(APIValidation)) mustBe JsSuccess(tstEmptyModel)
    }

    "write to json with empty data" in {
      Json.toJson[Director](tstEmptyModel)(Director.format(APIValidation)) mustBe tstEmptyJson
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

      def result(nino: String) = Json.fromJson[Director](json(nino))(Director.format(APIValidation))

      shouldHaveErrors(result("BG098765B"), JsPath() \ "nino", Seq(JsonValidationError("error.pattern")))

      shouldHaveErrors(result("AD098765D"), JsPath() \ "nino", Seq(JsonValidationError("error.pattern")))

      shouldHaveErrors(result("CV098765C"), JsPath() \ "nino", Seq(JsonValidationError("error.pattern")))

      shouldHaveErrors(result("SR098765Z"), JsPath() \ "nino", Seq(JsonValidationError("error.pattern")))

      shouldHaveErrors(result("SR098765Z"), JsPath() \ "nino", Seq(JsonValidationError("error.pattern")))

      shouldHaveErrors(result("SR 09 87 65 C"), JsPath() \ "nino", Seq(JsonValidationError("error.pattern")))
    }
  }

  "Title" should {
    "allow space in a tile" in {
      val tstJson = Json.parse(
        s"""{
           |  "title":"Sir "
           |}""".stripMargin)

      val tstModel = Name(None,None,None,Some("Sir "))
      Json.fromJson[Name](tstJson)(Name.format(APIValidation)) mustBe JsSuccess(tstModel)
    }
  }
}
