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
import play.api.libs.json._

class DigitalContactDetailsSpec extends PAYERegSpec with JsonFormatValidation {

  val dcdFormatter = DigitalContactDetails.reads(APIValidation)

  "DigitalContactDetails" should {
    "write to json" in {
      val json = Json.parse(
        s"""
           |{
           |  "email":"test@test.com",
           |  "mobileNumber":"07123456789",
           |  "phoneNumber":"0123 456 789"
           |}
        """.stripMargin)

      val tstDigitalContactDetails = DigitalContactDetails(
        email = Some("test@test.com"),
        mobileNumber = Some("07123456789"),
        phoneNumber = Some("0123 456 789")
      )

      Json.toJson(tstDigitalContactDetails)(DigitalContactDetails.writes) mustBe json
    }
  }

  "Creating a DigitalContactDetails model from Json" should {
    "complete successfully from full Json" in {
      val json = Json.parse(
        s"""
           |{
           |  "email":"test@test.com",
           |  "mobileNumber":"07123456789",
           |  "phoneNumber":"0123 456 789"
           |}
        """.stripMargin)

      val tstDigitalContactDetails = DigitalContactDetails(
        email = Some("test@test.com"),
        mobileNumber = Some("07123456789"),
        phoneNumber = Some("0123 456 789")
      )

      Json.fromJson[DigitalContactDetails](json)(dcdFormatter) mustBe JsSuccess(tstDigitalContactDetails)
    }

    "complete successfully from partial Json" when {
      "only email is completed" in {
        val json = Json.parse(
          s"""
             |{
             |  "email":"test@test.jumble.com"
             |}
        """.stripMargin)

        val tstDigitalContactDetails = DigitalContactDetails(
          email = Some("test@test.jumble.com"),
          mobileNumber = None,
          phoneNumber = None
        )

        Json.fromJson[DigitalContactDetails](json)(dcdFormatter) mustBe JsSuccess(tstDigitalContactDetails)
      }

      "only email is completed and it has a suffix of 11 characters" in {
        val json = Json.parse(
          s"""
             |{
             |  "email":"test@test.jumble.ccoommccoom"
             |}
        """.stripMargin)

        val tstDigitalContactDetails = DigitalContactDetails(
          email = Some("test@test.jumble.ccoommccoom"),
          mobileNumber = None,
          phoneNumber = None
        )

        Json.fromJson[DigitalContactDetails](json)(dcdFormatter) mustBe JsSuccess(tstDigitalContactDetails)
      }

      "only mobile number is completed" in {
        val json = Json.parse(
          s"""
             |{
             |  "mobileNumber":"07123456789"
             |}
        """.stripMargin)

        val tstDigitalContactDetails = DigitalContactDetails(
          email = None,
          mobileNumber = Some("07123456789"),
          phoneNumber = None
        )

        Json.fromJson[DigitalContactDetails](json)(dcdFormatter) mustBe JsSuccess(tstDigitalContactDetails)
      }
      "only phone number is completed" in {
        val json = Json.parse(
          s"""
             |{
             |  "phoneNumber":"0123456789"
             |}
        """.stripMargin)

        val tstDigitalContactDetails = DigitalContactDetails(
          email = None,
          mobileNumber = None,
          phoneNumber = Some("0123456789")
        )

        Json.fromJson[DigitalContactDetails](json)(dcdFormatter) mustBe JsSuccess(tstDigitalContactDetails)
      }
    }

    "fail" when {
      "email is too long" in {
        val json = Json.parse(
          s"""
             |{
             |  "email":"test@testWithExtraCharactersWhichTakeTheLengthToOneInExcessOfSeventyWhi",
             |  "mobileNumber":"07123456789",
             |  "phoneNumber":"0123456789"
             |}
        """.stripMargin)

        val res = Json.fromJson[DigitalContactDetails](json)(dcdFormatter)
        val expectedErrs = Map(JsPath() \ "email" -> Seq(JsonValidationError("Invalid email pattern")))
        shouldHaveErrors[DigitalContactDetails](res, expectedErrs)
      }
      "email is of the wrong pattern" in {
        val json = Json.parse(
          s"""
             |{
             |  "email":"test_test.com",
             |  "mobileNumber":"07123456789",
             |  "phoneNumber":"0123456789"
             |}
        """.stripMargin)

        val res = Json.fromJson[DigitalContactDetails](json)(dcdFormatter)
        val expectedErrs = Map(JsPath() \ "email" -> Seq(JsonValidationError("Invalid email pattern")))
        shouldHaveErrors[DigitalContactDetails](res, expectedErrs)
      }
      "email has no start" in {
        val json = Json.parse(
          s"""
             |{
             |  "email":"@test.com",
             |  "mobileNumber":"07123456789",
             |  "phoneNumber":"0123456789"
             |}
        """.stripMargin)

        val res = Json.fromJson[DigitalContactDetails](json)(dcdFormatter)
        val expectedErrs = Map(JsPath() \ "email" -> Seq(JsonValidationError("Invalid email pattern")))
        shouldHaveErrors[DigitalContactDetails](res, expectedErrs)
      }
      "email has no tail" in {
        val json = Json.parse(
          s"""
             |{
             |  "email":"test@",
             |  "mobileNumber":"07123456789",
             |  "phoneNumber":"0123456789"
             |}
        """.stripMargin)

        val res = Json.fromJson[DigitalContactDetails](json)(dcdFormatter)
        val expectedErrs = Map(JsPath() \ "email" -> Seq(JsonValidationError("Invalid email pattern")))
        shouldHaveErrors[DigitalContactDetails](res, expectedErrs)
      }
      "email has no domain suffix" in {
        val json = Json.parse(
          s"""
             |{
             |  "email":"test@test",
             |  "mobileNumber":"07123456789",
             |  "phoneNumber":"0123456789"
             |}
        """.stripMargin)

        val res = Json.fromJson[DigitalContactDetails](json)(dcdFormatter)
        val expectedErrs = Map(JsPath() \ "email" -> Seq(JsonValidationError("Invalid email pattern")))
        shouldHaveErrors[DigitalContactDetails](res, expectedErrs)
      }
    }

    "email has a domain suffix of 12 chars" in {
      val json = Json.parse(
        s"""
           |{
           |  "email":"test@test.ccoommccoomm",
           |  "mobileNumber":"07123456789",
           |  "phoneNumber":"0123456789"
           |}
        """.stripMargin)

      val res = Json.fromJson[DigitalContactDetails](json)(dcdFormatter)
      val expectedErrs = Map(JsPath() \ "email" -> Seq(JsonValidationError("Invalid email pattern")))
      shouldHaveErrors[DigitalContactDetails](res, expectedErrs)
    }

    "phone number is too long" in {
      val json = Json.parse(
        s"""
           |{
           |  "email":"test@test.com",
           |  "mobileNumber":"07123456789",
           |  "phoneNumber":"012345678912345678901"
           |}
        """.stripMargin)

      val res = Json.fromJson[DigitalContactDetails](json)(dcdFormatter)
      val expectedErrs = Map(JsPath() \ "phoneNumber" -> Seq(JsonValidationError("Invalid phone number pattern")))
      shouldHaveErrors[DigitalContactDetails](res, expectedErrs)
    }

    "phone number contains invalid characters" in {
      val json = Json.parse(
        s"""
           |{
           |  "email":"test@test.com",
           |  "mobileNumber":"07123456789",
           |  "phoneNumber":"0123456789ab1"
           |}
        """.stripMargin)

      val res = Json.fromJson[DigitalContactDetails](json)(dcdFormatter)
      val expectedErrs = Map(JsPath() \ "phoneNumber" -> Seq(JsonValidationError("Invalid phone number pattern")))
      shouldHaveErrors[DigitalContactDetails](res, expectedErrs)
    }

    "mobile number is too long" in {
      val json = Json.parse(
        s"""
           |{
           |  "email":"test@test.com",
           |  "mobileNumber":"012345678912345678901",
           |  "phoneNumber":"0123456789"
           |}
        """.stripMargin)

      val res = Json.fromJson[DigitalContactDetails](json)(dcdFormatter)
      val expectedErrs = Map(JsPath() \ "mobileNumber" -> Seq(JsonValidationError("Invalid phone number pattern")))
      shouldHaveErrors[DigitalContactDetails](res, expectedErrs)
    }

    "mobile number contains invalid characters" in {
      val json = Json.parse(
        s"""
           |{
           |  "email":"test@test.com",
           |  "mobileNumber":"071234567+89",
           |  "phoneNumber":"01234567891"
           |}
        """.stripMargin)

      val res = Json.fromJson[DigitalContactDetails](json)(dcdFormatter)
      val expectedErrs = Map(JsPath() \ "mobileNumber" -> Seq(JsonValidationError("Invalid phone number pattern")))
      shouldHaveErrors[DigitalContactDetails](res, expectedErrs)
    }
  }
}
