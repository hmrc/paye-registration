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

class CompanyDetailsSpec extends UnitSpec with JsonFormatValidation {

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
          |    "phoneNumber":"012345",
          |    "mobileNumber":"543210"
          |  }
          |}
        """.stripMargin)

      val tstCompanyDetails = CompanyDetails(
        companyName = " Test Company",
        tradingName = Some("Test Trading Name"),
        Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), None, Some("roAuditRef")),
        Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK")),
        DigitalContactDetails(Some("test@email.com"), Some("012345"), Some("543210"))
      )

      Json.fromJson[CompanyDetails](json) shouldBe JsSuccess(tstCompanyDetails)
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
           |    "phoneNumber":"012345"
           |  }
           |}
        """.stripMargin)

      val tstCompanyDetails = CompanyDetails(
        companyName = "Test-Company",
        tradingName = Some(".Test, (&') Trading/Name!"),
        Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK")),
        Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK"), Some("ppobAuditRef")),
        DigitalContactDetails(None, Some("012345"), None)
      )

      Json.fromJson[CompanyDetails](json) shouldBe JsSuccess(tstCompanyDetails)
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

      "it contains invalid characters" in {
        val json = tstJson("Test£Company")

        val result = Json.fromJson[CompanyDetails](json)
        shouldHaveErrors(result, JsPath() \ "companyName", Seq(ValidationError("error.pattern")))
      }
      "it is too long" in {
        val longName = List.fill(161)('a').mkString
        val json = tstJson(longName)

        val result = Json.fromJson[CompanyDetails](json)
        shouldHaveErrors(result, JsPath() \ "companyName", Seq(ValidationError("error.pattern")))
      }
      "it is too short" in {
        val json = tstJson("")

        val result = Json.fromJson[CompanyDetails](json)
        shouldHaveErrors(result, JsPath() \ "companyName", Seq(ValidationError("error.pattern")))
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

        val result = Json.fromJson[CompanyDetails](json)
        shouldHaveErrors(result, JsPath() \ "tradingName", Seq(ValidationError("error.pattern")))
      }
      "it contains invalid characters 2" in {
        val json = tstJson(" Test Company")

        val result = Json.fromJson[CompanyDetails](json)
        shouldHaveErrors(result, JsPath() \ "tradingName", Seq(ValidationError("error.pattern")))
      }
      "it is too long" in {
        val longName = List.fill(36)('a').mkString
        val json = tstJson(longName)

        val result = Json.fromJson[CompanyDetails](json)
        shouldHaveErrors(result, JsPath() \ "tradingName", Seq(ValidationError("error.pattern")))
      }
      "it is too short" in {
        val json = tstJson("")

        val result = Json.fromJson[CompanyDetails](json)
        shouldHaveErrors(result, JsPath() \ "tradingName", Seq(ValidationError("error.pattern")))
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

      Json.fromJson[Address](json) shouldBe JsSuccess(tstAddress)
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

      Json.fromJson[Address](json) shouldBe JsSuccess(tstAddress)
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

      Json.fromJson[Address](json) shouldBe JsSuccess(tstAddress)
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

      Json.fromJson[Address](json) shouldBe JsSuccess(tstAddress)
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

      val result = Json.fromJson[Address](json)
      shouldHaveErrors(result, JsPath() \ "line1", Seq(ValidationError("error.path.missing")))
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

        val result = Json.fromJson[Address](json)
        shouldHaveErrors(result, JsPath() \ "line2", Seq(ValidationError("error.path.missing")))
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

        val result = Json.fromJson[Address](json)
        shouldHaveErrors(result,
          Map(
            JsPath() \ "line1" -> Seq(ValidationError("invalid address line pattern")),
            JsPath() \ "line2" -> Seq(ValidationError("invalid address line pattern")),
            JsPath() \ "line3" -> Seq(ValidationError("invalid address line pattern")),
            JsPath() \ "line4" -> Seq(ValidationError("invalid address line 4 pattern"))
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

        val result = Json.fromJson[Address](json)
        shouldHaveErrors(result,
          Map(
            JsPath() \ "line1" -> Seq(ValidationError("invalid address line pattern")),
            JsPath() \ "line2" -> Seq(ValidationError("invalid address line pattern")),
            JsPath() \ "line3" -> Seq(ValidationError("invalid address line pattern")),
            JsPath() \ "line4" -> Seq(ValidationError("invalid address line 4 pattern"))
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

        val result = Json.fromJson[Address](json)
        shouldHaveErrors(result, JsPath() \ "postCode", Seq(ValidationError("invalid postcode")))
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

        val result = Json.fromJson[Address](json)
        shouldHaveErrors(result, JsPath() \ "postCode", Seq(ValidationError("invalid postcode")))
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

        val result = Json.fromJson[Address](json)
        shouldHaveErrors(result, JsPath() \ "postCode", Seq(ValidationError("invalid postcode")))
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

        val result = Json.fromJson[Address](json)
        shouldHaveErrors(result, JsPath() \ "postCode", Seq(ValidationError("invalid postcode")))
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

        val result = Json.fromJson[Address](json)
        shouldHaveErrors(result, JsPath() \ "postCode", Seq(ValidationError("invalid postcode")))
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

        val result = Json.fromJson[Address](json)
        shouldHaveErrors(result, JsPath() \ "country", Seq(ValidationError("invalid country")))
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

        val result = Json.fromJson[Address](json)
        shouldHaveErrors(result, JsPath() \ "country", Seq(ValidationError("invalid country")))
      }
      "neither country nor postcode is defined" in {
        val json = Json.parse(
          s"""
             |{
             |  "line1":"14 St Test Walk",
             |  "line2":"Testley"
             |}
        """.stripMargin)

        val result = Json.fromJson[Address](json)
        shouldHaveErrors(result, JsPath(), Seq(ValidationError("neither postcode nor country was completed")))
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

        val result = Json.fromJson[Address](json)
        shouldHaveErrors(result, JsPath(), Seq(ValidationError("both postcode and country were completed")))
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

      Json.toJson[Address](addr)(Address.writesDES) shouldBe json
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