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

import java.time.LocalDate

class EmploymentSpec extends UnitSpec with JsonFormatValidation {

  "Creating a Employment model from Json" should {

    val date = LocalDate.of(2016, 12, 20)

    "complete successfully from full Json" in {
      val json = Json.parse(
        s"""
          |{
          |  "first-payment-date": "$date",
          |  "cis": true,
          |  "employees": true,
          |  "ocpn": true
          |}
        """.stripMargin)

      val testEmployment = Employment(employees = true, Some(true), subcontractors = true, date)

      Json.fromJson[Employment](json) shouldBe JsSuccess(testEmployment)
    }

    "complete successfully from Json with no OCPN" in {
      val json = Json.parse(
        s"""
           |{
           |  "first-payment-date": "$date",
           |  "cis": true,
           |  "employees": true
           |}
        """.stripMargin)

      val testEmployment = Employment(employees = true, None, subcontractors = true, date)

      Json.fromJson[Employment](json) shouldBe JsSuccess(testEmployment)
    }

    "fail from Json with invalid date" ignore {
      val json = Json.parse(
        s"""
           |{
           |  "first-payment-date": "2-12-2016",
           |  "cis": true,
           |  "employees": true,
           |  "ocpn": true
           |}
        """.stripMargin)

      val result = Json.fromJson[Employment](json)
      shouldHaveErrors(result, JsPath() \ "first-payment" \ "payment-date", Seq(ValidationError(
        "error.expected.date.isoformat")))
    }
  }
}
