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

import models.validation.APIValidation
import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, JsSuccess, Json}
import uk.gov.hmrc.play.test.UnitSpec

class PAYEContactSpec extends UnitSpec with JsonFormatValidation {

  val payeContactDetailsFormatter = PAYEContactDetails.formatter(APIValidation)

  "Creating a PAYEContactDetails model from Json" should {
    "complete successfully from full Json" in {
      val json = Json.parse(
        s"""
           |{
           |  "name":"Luis Fernandez",
           |  "digitalContactDetails" : {
           |    "email":"test@test.com",
           |    "mobileNumber":"07123456789",
           |    "phoneNumber":"0123456789"
           |  }
           |}
        """.stripMargin)

      val tstPAYEContactDetails = PAYEContactDetails(
        name = "Luis Fernandez",
        digitalContactDetails = DigitalContactDetails(
          email = Some("test@test.com"),
          mobileNumber = Some("07123456789"),
          phoneNumber = Some("0123456789")
        )
      )

      Json.fromJson[PAYEContactDetails](json)(PAYEContactDetails.formatter(APIValidation)) shouldBe JsSuccess(tstPAYEContactDetails)
    }

    "complete successfully from Json with incomplete digital contact details" in {
      val json = Json.parse(
        s"""
           |{
           |  "name":"Luis-Fernandez",
           |  "digitalContactDetails" : {
           |    "email":"test@test.com",
           |    "phoneNumber":"0123456789"
           |  }
           |}
        """.stripMargin)

      val tstPAYEContactDetails = PAYEContactDetails(
        name = "Luis-Fernandez",
        digitalContactDetails = DigitalContactDetails(
          email = Some("test@test.com"),
          mobileNumber = None,
          phoneNumber = Some("0123456789")
        )
      )

      Json.fromJson[PAYEContactDetails](json)(payeContactDetailsFormatter) shouldBe JsSuccess(tstPAYEContactDetails)
    }

    "fail" when {
      def contact(name: String) = Json.parse(
        s"""
           |{
           |  "name":"$name",
           |  "digitalContactDetails" : {
           |    "email":"test@test.com",
           |    "phoneNumber":"0123456789"
           |  }
           |}
        """.stripMargin)
      "contact name is invalid" in {
        val json = contact("Luis@Fernandez")

        val result = Json.fromJson[PAYEContactDetails](json)(payeContactDetailsFormatter)
        shouldHaveErrors(result, JsPath() \ "name", Seq(ValidationError("error.pattern")))
      }
      "contact name is too long" in {
        val json = contact(List.fill(101)('a').mkString)

        val result = Json.fromJson[PAYEContactDetails](json)(payeContactDetailsFormatter)
        shouldHaveErrors(result, JsPath() \ "name", Seq(ValidationError("error.pattern")))
      }
      "contact name is too short" in {
        val json = contact("")

        val result = Json.fromJson[PAYEContactDetails](json)(payeContactDetailsFormatter)
        shouldHaveErrors(result, JsPath() \ "name", Seq(ValidationError("error.pattern")))
      }
    }
  }

  "Creating a PAYEContact model from Json" should {
    "complete successfully from full Json" in {
      val json = Json.parse(
        s"""
           |{
           |  "contactDetails": {
           |    "name":"Luis Fernandez",
           |    "digitalContactDetails" : {
           |      "email":"test@test.com",
           |      "mobileNumber":"07123456789",
           |      "phoneNumber":"0123456789"
           |    }
           |  },
           |  "correspondenceAddress": {
           |    "line1":"19 St Walk",
           |    "line2":"Testley CA",
           |    "line3":"Testford",
           |    "line4":"Testshire",
           |    "postCode":"TE4 1ST"
           |  }
           |}
        """.stripMargin)

      val tstPAYEContact = PAYEContact(
        contactDetails = PAYEContactDetails(
          name = "Luis Fernandez",
          digitalContactDetails = DigitalContactDetails(
            email = Some("test@test.com"),
            mobileNumber = Some("07123456789"),
            phoneNumber = Some("0123456789")
          )
        ),
        correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), None)
      )

      Json.fromJson[PAYEContact](json) shouldBe JsSuccess(tstPAYEContact)
    }

    "complete successfully from incomplete Correspondence Address Json" in {
      val json = Json.parse(
        s"""
           |{
           |  "contactDetails": {
           |    "name":"Luis Fernandez",
           |    "digitalContactDetails" : {
           |      "email":"test@test.com",
           |      "mobileNumber":"07123456789",
           |      "phoneNumber":"0123456789"
           |    }
           |  },
           |  "correspondenceAddress": {
           |    "line1":"19 St Walk",
           |    "line2":"Testley CA",
           |    "line4":"Testshire",
           |    "country":"UK"
           |  }
           |}
        """.stripMargin)

      val tstPAYEContact = PAYEContact(
        contactDetails = PAYEContactDetails(
          name = "Luis Fernandez",
          digitalContactDetails = DigitalContactDetails(
            email = Some("test@test.com"),
            mobileNumber = Some("07123456789"),
            phoneNumber = Some("0123456789")
          )
        ),
        correspondenceAddress = Address("19 St Walk", "Testley CA", None, Some("Testshire"), None, Some("UK"))
      )

      Json.fromJson[PAYEContact](json) shouldBe JsSuccess(tstPAYEContact)
    }
  }
}
