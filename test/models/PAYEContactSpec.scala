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

class PAYEContactSpec extends UnitSpec with JsonFormatValidation {
  "Creating a PAYEContact model from Json" should {
    "complete successfully from full Json" in {
      val json = Json.parse(
        s"""
           |{
           |  "name":"Luis Fernandez",
           |  "digitalContact" : {
           |    "email":"test@test.com",
           |    "mobileNumber":"07123456789",
           |    "phoneNumber":"0123456789"
           |  }
           |}
        """.stripMargin)

      val tstPAYEContact = PAYEContact(
        name = "Luis Fernandez",
        digitalContact = DigitalContactDetails(
          email = Some("test@test.com"),
          mobileNumber = Some("07123456789"),
          phoneNumber = Some("0123456789")
        )
      )

      Json.fromJson[PAYEContact](json) shouldBe JsSuccess(tstPAYEContact)
    }

    "complete successfully from Json with incomplete digital contact details" in {
      val json = Json.parse(
        s"""
           |{
           |  "name":"Luis Fernandez",
           |  "digitalContact" : {
           |    "email":"test@test.com",
           |    "phoneNumber":"0123456789"
           |  }
           |}
        """.stripMargin)

      val tstPAYEContact = PAYEContact(
        name = "Luis Fernandez",
        digitalContact = DigitalContactDetails(
          email = Some("test@test.com"),
          mobileNumber = None,
          phoneNumber = Some("0123456789")
        )
      )

      Json.fromJson[PAYEContact](json) shouldBe JsSuccess(tstPAYEContact)
    }

    "fail from Json with invalid paye contact name" in {
      val json = Json.parse(
        s"""
           |{
           |  "name":"Luis@Fernandez",
           |  "digitalContact" : {
           |    "email":"test@test.com",
           |    "phoneNumber":"0123456789"
           |  }
           |}
        """.stripMargin)

      val result = Json.fromJson[PAYEContact](json)
      shouldHaveErrors(result, JsPath() \ "name", Seq(ValidationError("error.pattern")))
    }
  }
}
