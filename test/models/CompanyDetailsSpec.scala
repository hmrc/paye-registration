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

class CompanyDetailsSpec extends UnitSpec with JsonFormatValidation {

  "Creating a CompanyDetails model from Json" should {
    "complete successfully from full Json" in {
      val json = Json.parse(
        s"""
          |{
          |  "crn":"Ac123456",
          |  "companyName":"Test Company",
          |  "tradingName":"Test Trading Name"
          |}
        """.stripMargin)

      val tstCompanyDetails = CompanyDetails(crn = Some("Ac123456"), companyName = "Test Company", tradingName = Some("Test Trading Name"))

      Json.fromJson[CompanyDetails](json) shouldBe JsSuccess(tstCompanyDetails)
    }

    "complete successfully from Json with no CRN" in {
      val json = Json.parse(
        s"""
           |{
           |  "companyName":"Test Company",
           |  "tradingName":"Test Trading Name"
           |}
        """.stripMargin)

      val tstCompanyDetails = CompanyDetails(crn = None, companyName = "Test Company", tradingName = Some("Test Trading Name"))

      Json.fromJson[CompanyDetails](json) shouldBe JsSuccess(tstCompanyDetails)
    }

    "fail from Json with invalid company name" in {
      val json = Json.parse(
        s"""
           |{
           |  "crn":"Ac123456",
           |  "companyName":"Test£Company",
           |  "tradingName":"Test Trading Name"
           |}
        """.stripMargin)

      val result = Json.fromJson[CompanyDetails](json)
      shouldHaveErrors(result, JsPath() \ "companyName", Seq(ValidationError("error.pattern")))
    }

    "fail from Json with invalid crn" in {
      val json = Json.parse(
        s"""
           |{
           |  "crn":"AX123456",
           |  "companyName":"Test Company",
           |  "tradingName":"Test Trading Name"
           |}
        """.stripMargin)

      val result = Json.fromJson[CompanyDetails](json)
      shouldHaveErrors(result, JsPath() \ "crn", Seq(ValidationError("error.pattern")))
    }
  }

}
