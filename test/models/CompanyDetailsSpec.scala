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

package models

import models.validation.{APIValidation, DesValidation}
import play.api.libs.json.{JsPath, JsSuccess, Json, JsonValidationError}
import uk.gov.hmrc.play.test.UnitSpec

class CompanyDetailsSpec extends UnitSpec with JsonFormatValidation {

  val cdFormatter = CompanyDetails.format(APIValidation)

  "Creating a CompanyDetails model from Json" should {
    "complete successfully from full Json" in {
      val json = Json.parse(
        s"""
          |{
          |  "companyName":" Test Company",
          |  "tradingName":"Test Trading Name",
          |  "roAddress": {
          |    "line1":"14 St Test Walk",
          |    "line2":"Testley",
          |    "line3":"Testford",
          |    "line4":"Testshire",
          |    "postCode":"TE1 1ST",
          |    "auditRef":"roAuditRef"
          |  },
          |  "ppobAddress": {
          |    "line1":"15 St Walk",
          |    "line2":"Testley",
          |    "line3":"Testford",
          |    "line4":"Testshire",
          |    "country":"UK"
          |  },
          |  "businessContactDetails": {
          |    "email":"test@email.com",
          |    "phoneNumber":"0123459999",
          |    "mobileNumber":"5432109999"
          |  }
          |}
        """.stripMargin)

      val tstCompanyDetails = CompanyDetails(
        companyName = " Test Company",
        tradingName = Some("Test Trading Name"),
        Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), None, Some("roAuditRef")),
        Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK")),
        DigitalContactDetails(Some("test@email.com"), Some("0123459999"), Some("5432109999"))
      )

      Json.fromJson[CompanyDetails](json)(cdFormatter) shouldBe JsSuccess(tstCompanyDetails)
    }

    "complete successfully from Json with no CRN" in {
      val json = Json.parse(
        s"""
           |{
           |  "companyName":"Test-Company",
           |  "tradingName":".Test, (&') Trading/Name!",
           |  "roAddress": {
           |    "line1":"14 St Test Walk",
           |    "line2":"Testley",
           |    "line3":"Testford",
           |    "line4":"Testshire",
           |    "country":"UK"
           |  },
           |  "ppobAddress": {
           |    "line1":"15 St Walk",
           |    "line2":"Testley",
           |    "line3":"Testford",
           |    "line4":"Testshire",
           |    "country":"UK",
           |    "auditRef":"ppobAuditRef"
           |  },
           |  "businessContactDetails": {
           |    "phoneNumber":"0123459999"
           |  }
           |}
        """.stripMargin)

      val tstCompanyDetails = CompanyDetails(
        companyName = "Test-Company",
        tradingName = Some(".Test, (&') Trading/Name!"),
        Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK")),
        Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK"), Some("ppobAuditRef")),
        DigitalContactDetails(None, Some("0123459999"), None)
      )

      Json.fromJson[CompanyDetails](json)(cdFormatter) shouldBe JsSuccess(tstCompanyDetails)
    }

    "fail on company name" when {

      def tstJson(cName: String) = Json.parse(
        s"""
           |{
           |  "companyName":"$cName",
           |  "tradingName":"Test Trading Name",
           |  "roAddress": {
           |    "line1":"14 St Test Walk",
           |    "line2":"Testley",
           |    "postCode":"TE1 1ST"
           |  },
           |  "ppobAddress": {
           |    "line1":"15 St Walk",
           |    "line2":"Testley",
           |    "postCode":"TE4 1ST"
           |  },
           |  "businessContactDetails": {
           |    "email":"email@test.co.uk"
           |  }
           |}
        """.stripMargin)

      "it is too long" in {
        val longName = List.fill(161)('a').mkString
        val json = tstJson(longName)

        val result = Json.fromJson[CompanyDetails](json)(cdFormatter)
        shouldHaveErrors(result, JsPath() \ "companyName", Seq(JsonValidationError("Invalid company name")))
      }
      "it is too short" in {
        val json = tstJson("")

        val result = Json.fromJson[CompanyDetails](json)(cdFormatter)
        shouldHaveErrors(result, JsPath() \ "companyName", Seq(JsonValidationError("Invalid company name")))
      }
    }

    "fail on trading name" when {

      def tstJson(tName: String) = Json.parse(
        s"""
           |{
           |  "companyName":"Test-Company234",
           |  "tradingName":"$tName",
           |  "roAddress": {
           |    "line1":"14 St Test Walk",
           |    "line2":"Testley",
           |    "postCode":"TE1 1ST"
           |  },
           |  "ppobAddress": {
           |    "line1":"15 St Walk",
           |    "line2":"Testley",
           |    "country":"UK"
           |  },
           |  "businessContactDetails": {
           |    "email":"email@test.co.uk"
           |  }
           |}
        """.stripMargin)

      "it contains invalid characters" in {
        val json = tstJson("Test£Company")

        val result = Json.fromJson[CompanyDetails](json)(cdFormatter)
        shouldHaveErrors(result, JsPath() \ "tradingName", Seq(JsonValidationError("Invalid trading name")))
      }
      "it contains invalid characters 2" in {
        val json = tstJson(" Test Company")

        val result = Json.fromJson[CompanyDetails](json)(cdFormatter)
        shouldHaveErrors(result, JsPath() \ "tradingName", Seq(JsonValidationError("Invalid trading name")))
      }
      "it is too long" in {
        val longName = List.fill(36)('a').mkString
        val json = tstJson(longName)

        val result = Json.fromJson[CompanyDetails](json)(cdFormatter)
        shouldHaveErrors(result, JsPath() \ "tradingName", Seq(JsonValidationError("Invalid trading name")))
      }
      "it is too short" in {
        val json = tstJson("")

        val result = Json.fromJson[CompanyDetails](json)(cdFormatter)
        shouldHaveErrors(result, JsPath() \ "tradingName", Seq(JsonValidationError("Invalid trading name")))
      }
    }
  }

  "Creating a Address model from Json" should {
    "complete successfully from full Json" in {
      val json = Json.parse(
        s"""
           |{
           |  "line1":"14 St. Test-Walk",
           |  "line2":"Testley/Testerley",
           |  "line3":"Testford & Testershire",
           |  "line4":"Testshire",
           |  "country":"UK"
           |}
        """.stripMargin)

      val tstAddress = Address("14 St. Test-Walk", "Testley/Testerley", Some("Testford & Testershire"), Some("Testshire"), None, Some("UK"))

      Json.fromJson[Address](json)(Address.reads(APIValidation)) shouldBe JsSuccess(tstAddress)
    }

    "complete successfully from Json with no address line4" in {
      val json = Json.parse(
        """
           |{
           |  "line1":"14 St Test & Walk",
           |  "line2":"Testley\\Testerley",
           |  "line3":"Testford",
           |  "postCode":"TE1 1ST"
           |}
        """.stripMargin)

      val tstAddress = Address("14 St Test & Walk", "Testley\\Testerley", Some("Testford"), None, Some("TE1 1ST"), None)

      Json.fromJson[Address](json)(Address.reads(APIValidation)) shouldBe JsSuccess(tstAddress)
    }
    "complete successfully from Json with no country" in {
      val json = Json.parse(
        s"""
           |{
           |  "line1":"14 St. Test-Walk",
           |  "line2":"Testley/Testerley",
           |  "postCode":"TE1 1ST"
           |}
        """.stripMargin)

      val tstAddress = Address("14 St. Test-Walk", "Testley/Testerley", None, None, Some("TE1 1ST"), None)

      Json.fromJson[Address](json)(Address.reads(APIValidation)) shouldBe JsSuccess(tstAddress)
    }
    "complete successfully from Json with no postcode" in {
      val json = Json.parse(
        s"""
           |{
           |  "line1":"14 St. Test-Walk",
           |  "line2":"Testley/Testerley",
           |  "country":"UK"
           |}
        """.stripMargin)

      val tstAddress = Address("14 St. Test-Walk", "Testley/Testerley", None, None, None, Some("UK"))

      Json.fromJson[Address](json)(Address.reads(APIValidation)) shouldBe JsSuccess(tstAddress)
    }

    "fail from Json with invalid address" in {
      val json = Json.parse(
        s"""
           |{
           |  "line2":"Testley",
           |  "line3":"Testford",
           |  "line4":"Testshire",
           |  "country":"UK"
           |}
      """.stripMargin)

      val result = Json.fromJson[Address](json)(Address.reads(APIValidation))
      shouldHaveErrors(result, JsPath() \ "line1", Seq(JsonValidationError("error.path.missing")))
    }

    "fail" when {
      "address line 2 is missing" in {
        val json = Json.parse(
          s"""
             |{
             |  "line1":"14 St Test Walk",
             |  "line3":"Testford",
             |  "line4":"Testshire",
             |  "country":"UK"
             |}
      """.stripMargin)

        val result = Json.fromJson[Address](json)(Address.reads(APIValidation))
        shouldHaveErrors(result, JsPath() \ "line2", Seq(JsonValidationError("error.path.missing")))
      }
      "address lines are too long" in {
        val json = Json.parse(
          s"""
             |{
             |  "line1":"1234567891123456789212345678",
             |  "line2":"1234567891123456789212345678",
             |  "line3":"1234567891123456789212345678",
             |  "line4":"1234567891123456789",
             |  "country":"UK"
             |}
        """.stripMargin)

        val result = Json.fromJson[Address](json)(Address.reads(APIValidation))
        shouldHaveErrors(result,
          Map(
            JsPath() \ "line1" -> Seq(JsonValidationError("Invalid address line pattern")),
            JsPath() \ "line2" -> Seq(JsonValidationError("Invalid address line pattern")),
            JsPath() \ "line3" -> Seq(JsonValidationError("Invalid address line pattern")),
            JsPath() \ "line4" -> Seq(JsonValidationError("Invalid address line 4 pattern"))
          )
        )
      }
      "address lines begin with spaces" in {
        val json = Json.parse(
          s"""
             |{
             |  "line1":" 14 St Test Walk",
             |  "line2":" Testley",
             |  "line3":" Testford",
             |  "line4":" Testshire",
             |  "country":"UK"
             |}
        """.stripMargin)

        val result = Json.fromJson[Address](json)(Address.reads(APIValidation))
        shouldHaveErrors(result,
          Map(
            JsPath() \ "line1" -> Seq(JsonValidationError("Invalid address line pattern")),
            JsPath() \ "line2" -> Seq(JsonValidationError("Invalid address line pattern")),
            JsPath() \ "line3" -> Seq(JsonValidationError("Invalid address line pattern")),
            JsPath() \ "line4" -> Seq(JsonValidationError("Invalid address line 4 pattern"))
          )
        )
      }
      "there is no space in the postcode" in {
        val json = Json.parse(
          s"""
             |{
             |  "line1":"14 St Test Walk",
             |  "line2":"Testley",
             |  "postCode":"TE11ST"
             |}
        """.stripMargin)

        val result = Json.fromJson[Address](json)(Address.reads(APIValidation))
        shouldHaveErrors(result, JsPath() \ "postCode", Seq(JsonValidationError("Invalid postcode")))
      }
      "postcode is invalid 1" in {
        val json = Json.parse(
          s"""
             |{
             |  "line1":"14 St Test Walk",
             |  "line2":"Testley",
             |  "postCode":"TES 1ST"
             |}
        """.stripMargin)

        val result = Json.fromJson[Address](json)(Address.reads(APIValidation))
        shouldHaveErrors(result, JsPath() \ "postCode", Seq(JsonValidationError("Invalid postcode")))
      }
      "postcode is invalid 2" in {
        val json = Json.parse(
          s"""
             |{
             |  "line1":"14 St Test Walk",
             |  "line2":"Testley",
             |  "postCode":"TE1 AST"
             |}
        """.stripMargin)

        val result = Json.fromJson[Address](json)(Address.reads(APIValidation))
        shouldHaveErrors(result, JsPath() \ "postCode", Seq(JsonValidationError("Invalid postcode")))
      }
      "postcode is invalid 3" in {
        val json = Json.parse(
          s"""
             |{
             |  "line1":"14 St Test Walk",
             |  "line2":"Testley",
             |  "postCode":"TE1 1S"
             |}
        """.stripMargin)

        val result = Json.fromJson[Address](json)(Address.reads(APIValidation))
        shouldHaveErrors(result, JsPath() \ "postCode", Seq(JsonValidationError("Invalid postcode")))
      }
      "postcode is too short" in {
        val json = Json.parse(
          s"""
             |{
             |  "line1":"14 St Test Walk",
             |  "line2":"Testley",
             |  "postCode":""
             |}
        """.stripMargin)

        val result = Json.fromJson[Address](json)(Address.reads(APIValidation))
        shouldHaveErrors(result, JsPath() \ "postCode", Seq(JsonValidationError("Invalid postcode")))
      }
      "country is too short" in {
        val json = Json.parse(
          s"""
             |{
             |  "line1":"14 St Test Walk",
             |  "line2":"Testley",
             |  "country":""
             |}
        """.stripMargin)

        val result = Json.fromJson[Address](json)(Address.reads(APIValidation))
        shouldHaveErrors(result, JsPath() \ "country", Seq(JsonValidationError("Invalid country")))
      }
      "country is too long" in {
        val country = List.fill(21)('a').mkString
        val json = Json.parse(
          s"""
             |{
             |  "line1":"14 St Test Walk",
             |  "line2":"Testley",
             |  "country":"$country"
             |}
        """.stripMargin)

        val result = Json.fromJson[Address](json)(Address.reads(APIValidation))
        shouldHaveErrors(result, JsPath() \ "country", Seq(JsonValidationError("Invalid country")))
      }
      "neither country nor postcode is defined" in {
        val json = Json.parse(
          s"""
             |{
             |  "line1":"14 St Test Walk",
             |  "line2":"Testley"
             |}
        """.stripMargin)

        val result = Json.fromJson[Address](json)(Address.reads(APIValidation))
        shouldHaveErrors(result, JsPath(), Seq(JsonValidationError("neither postcode nor country was completed")))
      }
      "both country and postcode are defined" in {
        val json = Json.parse(
          s"""
             |{
             |  "line1":"14 St Test Walk",
             |  "line2":"Testley",
             |  "postCode":"TE1 1ST",
             |  "country":"UK"
             |}
        """.stripMargin)

        val result = Json.fromJson[Address](json)(Address.reads(APIValidation))
        shouldHaveErrors(result, JsPath(), Seq(JsonValidationError("both postcode and country were completed")))
      }
    }
  }

  "Writing an address" should {
    val addr = Address(
      line1 = "lineOne",
      line2 = "lineTwo",
      line3 = Some("lineThree"),
      line4 = None,
      postCode = Some("TE1 1ST"),
      country = None,
      auditRef = Some("auditReference")
    )

    "ignore audit ref to DES" in {
      val json = Json.parse(
      """
        |{
        |  "addressLine1":"lineOne",
        |  "addressLine2":"lineTwo",
        |  "addressLine3":"lineThree",
        |  "postcode":"TE1 1ST"
        |}
      """.stripMargin
      )

      Json.toJson[Address](addr)(Address.writes(DesValidation)) shouldBe json
    }

    "include audit ref by default" in {
      val json = Json.parse(
        """
          |{
          |  "line1":"lineOne",
          |  "line2":"lineTwo",
          |  "line3":"lineThree",
          |  "postCode":"TE1 1ST",
          |  "auditRef":"auditReference"
          |}
        """.stripMargin
      )

      Json.toJson[Address](addr) shouldBe json
    }
  }

}
