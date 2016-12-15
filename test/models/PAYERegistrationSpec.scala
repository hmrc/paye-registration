/*
 * Copyright 2016 HM Revenue & Customs
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

class PAYERegistrationSpec extends UnitSpec with JsonFormatValidation {

  "Creating a PAYERegistration model from Json" should {
    "complete successfully from full Json" in {
      val json = Json.parse(
        s"""
           |{
           |  "registrationID":"12345",
           |  "formCreationTimestamp":"2016-05-31",
           |  "companyDetails":
           |    {
           |      "crn":"Ac123456",
           |      "companyName":"Test Company",
           |      "tradingName":"Test Trading Name"
           |    }
           |}
        """.stripMargin)

      val tstPAYERegistration = PAYERegistration(
        registrationID = "12345",
        formCreationTimestamp = "2016-05-31",
        companyDetails = Some(
          CompanyDetails(crn = Some("Ac123456"), companyName = "Test Company", tradingName = "Test Trading Name")
        )
      )

      Json.fromJson[PAYERegistration](json) shouldBe JsSuccess(tstPAYERegistration)
    }

    "complete successfully from Json with no companyDetails" in {
      val json = Json.parse(
        s"""
           |{
           |  "registrationID":"12345",
           |  "formCreationTimestamp":"2016-05-31"
           |}
        """.stripMargin)

      val tstPAYERegistration = PAYERegistration(
        registrationID = "12345",
        formCreationTimestamp = "2016-05-31",
        companyDetails = None
      )

      Json.fromJson[PAYERegistration](json) shouldBe JsSuccess(tstPAYERegistration)
    }

    "fail from json without registrationID" in {
      val json = Json.parse(
        s"""
           |{
           |  "formCreationTimestamp":"2016-05-31",
           |  "companyDetails":
           |    {
           |      "crn":"Ac123456",
           |      "companyName":"Test Company",
           |      "tradingName":"Test Trading Name"
           |    }
           |}
        """.stripMargin)

      val result = Json.fromJson[PAYERegistration](json)
      shouldHaveErrors(result, JsPath() \ "registrationID", Seq(ValidationError("error.path.missing")))
    }
  }
}
