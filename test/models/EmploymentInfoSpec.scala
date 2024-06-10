/*
 * Copyright 2023 HM Revenue & Customs
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

import enums.Employing
import helpers.PAYERegSpec
import models.validation.{APIValidation, MongoValidation}
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.{JsObject, JsResultException, Json}

import java.time.LocalDate

class EmploymentInfoSpec extends PAYERegSpec with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    System.clearProperty("feature.system-date")
  }

  "creating an EmploymentInfo" should {
    "be successful" in {
      System.setProperty("feature.system-date", "2018-12-25T00:00:00Z")

      val incorpDate: LocalDate = LocalDate.of(2016, 12, 26)

      val json = Json.parse(
        """|{
           |   "employees": "alreadyEmploying",
           |   "firstPaymentDate": "2017-12-29",
           |   "construction": true,
           |   "subcontractors": true,
           |   "companyPension": true
           | }
        """.stripMargin).as[JsObject]

      val expectedModel = EmploymentInfo(
        employees = Employing.alreadyEmploying,
        firstPaymentDate = LocalDate.of(2017, 12, 29),
        construction = true,
        subcontractors = true,
        companyPension = Some(true)
      )

      json.as[EmploymentInfo](EmploymentInfo.format(APIValidation, Some(incorpDate))) mustBe expectedModel
    }

    "be unsuccessful with a wrong enum value" in {
      val json = Json.parse(
        """|{
           |   "employees": "wrongValue",
           |   "firstPaymentDate": "2017-12-29",
           |   "construction": true,
           |   "subcontractors": true,
           |   "companyPension": true
           | }
        """.stripMargin).as[JsObject]

      a[JsResultException] mustBe thrownBy(json.as[EmploymentInfo])
    }

    s"be unsuccessful when employees is set to ${Employing.alreadyEmploying} and missing incorporation date" in {
      val json = Json.parse(
        s"""|{
           |   "employees": "${Employing.alreadyEmploying}",
           |   "firstPaymentDate": "2017-12-29",
           |   "construction": true,
           |   "subcontractors": true,
           |   "companyPension": true
           | }
        """.stripMargin).as[JsObject]

      a[JsResultException] mustBe thrownBy(json.as[EmploymentInfo])
    }
  }

  "creating an EmploymentInfo with API Validation" should {
    "be successful" when {
      "company is incorporated more than 2 years ago, employees is alreadyEmploying and firstPaymentDate is exactly today - 2 years" in {
        System.setProperty("feature.system-date", "2018-12-25T00:00:00Z")

        val incorpDate: LocalDate = LocalDate.of(2016, 12, 24)
        val expectedModel = EmploymentInfo(
          employees = Employing.alreadyEmploying,
          firstPaymentDate = LocalDate.of(2016, 12, 25),
          construction = true,
          subcontractors = true,
          companyPension = Some(true)
        )

        val json = Json.parse(
          """|{
             |   "employees": "alreadyEmploying",
             |   "firstPaymentDate": "2016-12-25",
             |   "construction": true,
             |   "subcontractors": true,
             |   "companyPension": true
             | }
          """.stripMargin).as[JsObject]

        json.as[EmploymentInfo](EmploymentInfo.format(APIValidation, Some(incorpDate))) mustBe expectedModel
      }

      "company is incorporated less than 2 years ago, employees is alreadyEmploying and firstPaymentDate is exactly the incorporation date" in {
        System.setProperty("feature.system-date", "2018-12-25T00:00:00Z")

        val incorpDate: LocalDate = LocalDate.of(2016, 12, 26)
        val expectedModel = EmploymentInfo(
          employees = Employing.alreadyEmploying,
          firstPaymentDate = LocalDate.of(2016, 12, 26),
          construction = true,
          subcontractors = true,
          companyPension = Some(true)
        )

        val json = Json.parse(
          """|{
             |   "employees": "alreadyEmploying",
             |   "firstPaymentDate": "2016-12-26",
             |   "construction": true,
             |   "subcontractors": true,
             |   "companyPension": true
             | }
          """.stripMargin).as[JsObject]

        json.as[EmploymentInfo](EmploymentInfo.format(APIValidation, Some(incorpDate))) mustBe expectedModel
      }

      "employees is willEmployThisYear and firstPaymentDate is exactly today" in {
        System.setProperty("feature.system-date", "2018-12-25T00:00:00Z")

        val expectedModel = EmploymentInfo(
          employees = Employing.willEmployThisYear,
          firstPaymentDate = LocalDate.of(2018, 12, 25),
          construction = true,
          subcontractors = true,
          companyPension = None
        )

        val json = Json.parse(
          """|{
             |   "employees": "willEmployThisYear",
             |   "firstPaymentDate": "2018-12-25",
             |   "construction": true,
             |   "subcontractors": true
             | }
          """.stripMargin).as[JsObject]

        json.as[EmploymentInfo](EmploymentInfo.format(APIValidation)) mustBe expectedModel
      }

      "employees is notEmploying and firstPaymentDate is exactly today" in {
        System.setProperty("feature.system-date", "2018-12-25T00:00:00Z")

        val expectedModel = EmploymentInfo(
          employees = Employing.notEmploying,
          firstPaymentDate = LocalDate.of(2018, 12, 25),
          construction = true,
          subcontractors = true,
          companyPension = None
        )

        val json = Json.parse(
          """|{
             |   "employees": "notEmploying",
             |   "firstPaymentDate": "2018-12-25",
             |   "construction": true,
             |   "subcontractors": true
             | }
          """.stripMargin).as[JsObject]

        json.as[EmploymentInfo](EmploymentInfo.format(APIValidation)) mustBe expectedModel
      }

      "employees is willEmployNextYear and firstPaymentDate is exactly 06-04-currentYear" in {
        System.setProperty("feature.system-date", "2017-12-25T00:00:00Z")

        val expectedModel = EmploymentInfo(
          employees = Employing.willEmployNextYear,
          firstPaymentDate = LocalDate.of(2017, 4, 6),
          construction = true,
          subcontractors = true,
          companyPension = None
        )

        val json = Json.parse(
          """|{
             |   "employees": "willEmployNextYear",
             |   "firstPaymentDate": "2017-04-06",
             |   "construction": true,
             |   "subcontractors": true
             | }
          """.stripMargin).as[JsObject]

        json.as[EmploymentInfo](EmploymentInfo.format(APIValidation)) mustBe expectedModel
      }
      "employees is not alreadyEmploying and companyPension is not defined" in {
        System.setProperty("feature.system-date", "2017-12-25T00:00:00Z")

        val expectedModel = EmploymentInfo(
          employees = Employing.willEmployNextYear,
          firstPaymentDate = LocalDate.of(2017, 4, 6),
          construction = true,
          subcontractors = true,
          companyPension = None
        )

        val json = Json.parse(
          """|{
             |   "employees": "willEmployNextYear",
             |   "firstPaymentDate": "2017-04-06",
             |   "construction": true,
             |   "subcontractors": true
             | }
          """.stripMargin).as[JsObject]

        json.as[EmploymentInfo](EmploymentInfo.format(APIValidation)) mustBe expectedModel
      }

      "company is incorporated more than 2 years ago, employees is alreadyEmploying and firstPaymentDate is equal to today minus 2 years at start of Tax Year" in {
        System.setProperty("feature.system-date", "2018-12-25T00:00:00Z")

        val expectedModel = EmploymentInfo(
          employees = Employing.alreadyEmploying,
          firstPaymentDate = LocalDate.of(2016, 4, 6),
          construction = true,
          subcontractors = true,
          companyPension = Some(true)
        )

        val incorpDate: LocalDate = LocalDate.of(2015, 12, 24)
        val json = Json.parse(
          """|{
             |   "employees": "alreadyEmploying",
             |   "firstPaymentDate": "2016-04-06",
             |   "construction": true,
             |   "subcontractors": true,
             |   "companyPension": true
             | }
          """.stripMargin).as[JsObject]

        json.as[EmploymentInfo](EmploymentInfo.format(APIValidation, Some(incorpDate))) mustBe expectedModel
      }
    }

    "be unsuccessful" when {
      "company is incorporated more than 2 years ago, employees is alreadyEmploying and firstPaymentDate is before today minus 2 years at start of Tax Year" in {
        System.setProperty("feature.system-date", "2018-12-25T00:00:00Z")

        val incorpDate: LocalDate = LocalDate.of(2015, 12, 24)
        val json = Json.parse(
          """|{
             |   "employees": "alreadyEmploying",
             |   "firstPaymentDate": "2016-04-05",
             |   "construction": true,
             |   "subcontractors": true,
             |   "companyPension": true
             | }
          """.stripMargin).as[JsObject]

        json.validate[EmploymentInfo](EmploymentInfo.format(APIValidation, Some(incorpDate))).isError mustBe true
      }

      "company is incorporated less than 2 years ago, employees is alreadyEmploying and firstPaymentDate is before incorporation date" in {
        val incorpDate: LocalDate = LocalDate.of(2016, 12, 26)
        val json = Json.parse(
          """|{
             |   "employees": "alreadyEmploying",
             |   "firstPaymentDate": "2016-12-25",
             |   "construction": true,
             |   "subcontractors": true,
             |   "companyPension": true
             | }
          """.stripMargin).as[JsObject]

        json.validate[EmploymentInfo](EmploymentInfo.format(APIValidation, Some(incorpDate))).isError mustBe true
      }

      "employees is alreadyEmploying and firstPaymentDate is after today" in {
        System.setProperty("feature.system-date", "2018-12-25T00:00:00Z")

        val json = Json.parse(
          """|{
             |   "employees": "alreadyEmploying",
             |   "firstPaymentDate": "2018-12-26",
             |   "construction": true,
             |   "subcontractors": true,
             |   "companyPension": true
             | }
          """.stripMargin).as[JsObject]

        json.validate[EmploymentInfo](EmploymentInfo.format(APIValidation)).isError mustBe true
      }

      "employees is willEmployThisYear and firstPaymentDate is before today" in {
        System.setProperty("feature.system-date", "2018-12-25T00:00:00Z")

        val json = Json.parse(
          """|{
             |   "employees": "willEmployThisYear",
             |   "firstPaymentDate": "2018-12-24",
             |   "construction": true,
             |   "subcontractors": true,
             |   "companyPension": true
             | }
          """.stripMargin).as[JsObject]

        json.validate[EmploymentInfo](EmploymentInfo.format(APIValidation)).isError mustBe true
      }

      "employees is willEmployThisYear and firstPaymentDate is after today" in {
        System.setProperty("feature.system-date", "2018-12-25T00:00:00Z")

        val json = Json.parse(
          """|{
             |   "employees": "willEmployThisYear",
             |   "firstPaymentDate": "2018-12-26",
             |   "construction": true,
             |   "subcontractors": true,
             |   "companyPension": true
             | }
          """.stripMargin).as[JsObject]

        json.validate[EmploymentInfo](EmploymentInfo.format(APIValidation)).isError mustBe true
      }

      "employees is notEmploying and firstPaymentDate is before today" in {
        System.setProperty("feature.system-date", "2018-12-25T00:00:00Z")

        val json = Json.parse(
          """|{
             |   "employees": "notEmploying",
             |   "firstPaymentDate": "2018-12-24",
             |   "construction": true,
             |   "subcontractors": true,
             |   "companyPension": true
             | }
          """.stripMargin).as[JsObject]

        json.validate[EmploymentInfo](EmploymentInfo.format(APIValidation)).isError mustBe true
      }

      "employees is notEmploying and firstPaymentDate is after today" in {
        System.setProperty("feature.system-date", "2018-12-25T00:00:00Z")

        val json = Json.parse(
          """|{
             |   "employees": "notEmploying",
             |   "firstPaymentDate": "2018-12-26",
             |   "construction": true,
             |   "subcontractors": true,
             |   "companyPension": true
             | }
          """.stripMargin).as[JsObject]

        json.validate[EmploymentInfo](EmploymentInfo.format(APIValidation)).isError mustBe true
      }

      "employees is willEmployNextYear and firstPaymentDate is not exactly 06-04-currentYear (before)" in {
        System.setProperty("feature.system-date", "2017-04-05T00:00:00Z")

        val json = Json.parse(
          """|{
             |   "employees": "willEmployNextYear",
             |   "firstPaymentDate": "2017-04-05",
             |   "construction": true,
             |   "subcontractors": true,
             |   "companyPension": true
             | }
          """.stripMargin).as[JsObject]

        json.validate[EmploymentInfo](EmploymentInfo.format(APIValidation)).isError mustBe true
      }

      "employees is willEmployNextYear and firstPaymentDate is not exactly 06-04-currentYear (after)" in {
        System.setProperty("feature.system-date", "2018-04-05T00:00:00Z")

        val json = Json.parse(
          """|{
             |   "employees": "willEmployNextYear",
             |   "firstPaymentDate": "2017-04-07",
             |   "construction": true,
             |   "subcontractors": true,
             |   "companyPension": true
             | }
          """.stripMargin).as[JsObject]

        json.validate[EmploymentInfo](EmploymentInfo.format(APIValidation)).isError mustBe true
      }
      "construction is false and subcontractors is true" in {
        System.setProperty("feature.system-date", "2018-12-25T00:00:00Z")

        val json = Json.parse(
          """|{
             |   "employees": "alreadyEmploying",
             |   "firstPaymentDate": "2016-12-25",
             |   "construction": false,
             |   "subcontractors": true,
             |   "companyPension": true
             | }
          """.stripMargin).as[JsObject]
        json.validate[EmploymentInfo](EmploymentInfo.format(APIValidation)).isError mustBe true
      }
      "employees is alreadyEmploying and companyPension is not defined" in {
        System.setProperty("feature.system-date", "2018-12-25T00:00:00Z")

        val json = Json.parse(
          """|{
             |   "employees": "alreadyEmploying",
             |   "firstPaymentDate": "2016-12-25",
             |   "construction": true,
             |   "subcontractors": true
             | }
          """.stripMargin).as[JsObject]
        json.validate[EmploymentInfo](EmploymentInfo.format(APIValidation)).isError mustBe true
      }

      "employees is not alreadyEmploying and companyPension is defined" in {
        System.setProperty("feature.system-date", "2018-12-25T00:00:00Z")

        val json = Json.parse(
          """|{
             |   "employees": "notEmploying",
             |   "firstPaymentDate": "2016-12-25",
             |   "construction": true,
             |   "subcontractors": true,
             |   "companyPension": true
             | }
          """.stripMargin).as[JsObject]
        json.validate[EmploymentInfo](EmploymentInfo.format(APIValidation)).isError mustBe true
      }
    }
  }
  "reading from json with mongo validation" should {
    "be successful and return an EmploymentInfo Model" in {
      val json = Json.parse(
        """|{
           |   "employees": "alreadyEmploying",
           |   "firstPaymentDate": "1901-12-29",
           |   "construction": false,
           |   "subcontractors": true
           | }
        """.stripMargin).as[JsObject]
      val expectedModel = EmploymentInfo(
        employees = Employing.alreadyEmploying,
        firstPaymentDate = LocalDate.of(1901, 12, 29),
        construction = false,
        subcontractors = true,
        companyPension = None
      )

      Json.fromJson(json)(EmploymentInfo.mongoFormat).get mustBe expectedModel
    }
  }
  "writing to json with mongovalidation" should {
    "be successful and return json" in {
      val model = EmploymentInfo(
        employees = Employing.alreadyEmploying,
        firstPaymentDate = LocalDate.of(2017, 12, 29),
        construction = true,
        subcontractors = true,
        companyPension = Some(true)
      )
      val expectedJson = Json.parse(
        """|{
           |   "employees": "alreadyEmploying",
           |   "firstPaymentDate": "2017-12-29",
           |   "construction": true,
           |   "subcontractors": true,
           |   "companyPension": true
           | }
        """.stripMargin).as[JsObject]

      Json.toJson[EmploymentInfo](model)(EmploymentInfo.format(MongoValidation)) mustBe expectedJson
    }
    "be successful with invalid data and no companyPension" in {
      val model = EmploymentInfo(
        employees = Employing.alreadyEmploying,
        firstPaymentDate = LocalDate.of(1901, 12, 29),
        construction = false,
        subcontractors = true,
        companyPension = None
      )
      val expectedJson = Json.parse(
        """|{
           |   "employees": "alreadyEmploying",
           |   "firstPaymentDate": "1901-12-29",
           |   "construction": false,
           |   "subcontractors": true
           | }
        """.stripMargin).as[JsObject]
      Json.toJson[EmploymentInfo](model)(EmploymentInfo.format(MongoValidation)) mustBe expectedJson
    }
  }
}