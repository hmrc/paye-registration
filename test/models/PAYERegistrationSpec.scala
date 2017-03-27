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

import java.time.LocalDate

import enums.PAYEStatus
import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, JsSuccess, Json}
import uk.gov.hmrc.play.test.UnitSpec

class PAYERegistrationSpec extends UnitSpec with JsonFormatValidation {

  "Creating a PAYERegistration model from Json" should {
    "complete successfully from full Json" in {

      val date = LocalDate.of(2016, 12, 20)

      val json = Json.parse(
        s"""
           |{
           |  "registrationID":"12345",
           |  "transactionID" : "NNASD9789F",
           |  "internalID" : "09876",
           |  "formCreationTimestamp":"2016-05-31",
           |  "status":"draft",
           |  "completionCapacity":"Director",
           |  "companyDetails":
           |    {
           |      "companyName":"Test Company",
           |      "tradingName":"Test Trading Name",
           |      "roAddress": {
           |        "line1":"14 St Test Walk",
           |        "line2":"Testley",
           |        "line3":"Testford",
           |        "line4":"Testshire",
           |        "postCode":"TE1 1ST",
           |        "country":"UK"
           |      },
           |      "ppobAddress": {
           |        "line1":"15 St Walk",
           |        "line2":"Testley",
           |        "line3":"Testford",
           |        "line4":"Testshire",
           |        "postCode":"TE4 1ST",
           |        "country":"UK"
           |      },
           |      "businessContactDetails": {
           |        "email":"email@test.co.uk",
           |        "phoneNumber":"999",
           |        "mobileNumber":"00000"
           |      }
           |    },
           |  "directors" : [
           |    {
           |      "nino":"SR123456C",
           |      "director": {
           |        "forename":"Thierry",
           |        "other_forenames":"Dominique",
           |        "surname":"Henry",
           |        "title":"Sir"
           |      }
           |    },
           |    {
           |      "nino":"SR000009C",
           |      "director": {
           |        "forename":"David",
           |        "other_forenames":"Jesus",
           |        "surname":"Trezeguet",
           |        "title":"Mr"
           |      }
           |    }
           |  ],
           |  "payeContact": {
           |    "contactDetails": {
           |      "name": "toto tata",
           |      "digitalContactDetails": {
           |        "email": "payeemail@test.co.uk",
           |        "phoneNumber": "654",
           |        "mobileNumber": "12345"
           |      }
           |    },
           |    "correspondenceAddress": {
           |      "line1":"19 St Walk",
           |      "line2":"Testley CA",
           |      "line3":"Testford",
           |      "line4":"Testshire",
           |      "postCode":"TE4 1ST",
           |      "country":"UK"
           |    }
           |  },
           |  "employment": {
           |    "first-payment-date": "$date",
           |    "cis": true,
           |    "employees": true,
           |    "ocpn": true
           |  },
           |  "sicCodes": [
           |    {
           |      "code":"666",
           |      "description":"demolition"
           |    },
           |    {
           |      "description":"laundring"
           |    }
           |  ]
           |}
        """.stripMargin)

      val tstPAYERegistration = PAYERegistration(
        registrationID = "12345",
        transactionID = "NNASD9789F",
        internalID = "09876",
        acknowledgementReference = None,
        formCreationTimestamp = "2016-05-31",
        status = PAYEStatus.draft,
        completionCapacity = Some("Director"),
        companyDetails = Some(
          CompanyDetails(
            companyName = "Test Company",
            tradingName = Some("Test Trading Name"),
            Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), Some("UK")),
            Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK")),
            DigitalContactDetails(Some("email@test.co.uk"), Some("999"), Some("00000"))
          )
        ),
        directors = Seq(
          Director(
            Name(
              forename = Some("Thierry"),
              otherForenames = Some("Dominique"),
              surname = Some("Henry"),
              title = Some("Sir")
            ),
            Some("SR123456C")
          ),
          Director(
            Name(
              forename = Some("David"),
              otherForenames = Some("Jesus"),
              surname = Some("Trezeguet"),
              title = Some("Mr")
            ),
            Some("SR000009C")
          )
        ),
        payeContact = Some(
          PAYEContact(
            contactDetails = PAYEContactDetails(
              name = "toto tata",
              digitalContactDetails = DigitalContactDetails(
                Some("payeemail@test.co.uk"),
                Some("654"),
                Some("12345")
              )
            ),
            correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK"))
          )
        ),
        employment = Some(Employment(employees = true, Some(true), subcontractors = true, firstPaymentDate = date)),
        Seq(
          SICCode(code = Some("666"), description = Some("demolition")),
          SICCode(code = None, description = Some("laundring"))
        )
      )

      Json.fromJson[PAYERegistration](json) shouldBe JsSuccess(tstPAYERegistration)
    }

    "complete successfully from Json with no companyDetails" in {
      val json = Json.parse(
        s"""
           |{
           |  "registrationID":"12345",
           |  "transactionID" : "NNASD9789F",
           |  "internalID" : "09876",
           |  "formCreationTimestamp":"2016-05-31",
           |  "status":"draft",
           |  "directors" : [],
           |  "sicCodes" : []
           |}
        """.stripMargin)

      val tstPAYERegistration = PAYERegistration(
        registrationID = "12345",
        transactionID = "NNASD9789F",
        internalID = "09876",
        acknowledgementReference = None,
        formCreationTimestamp = "2016-05-31",
        status = PAYEStatus.draft,
        completionCapacity = None,
        companyDetails = None,
        Seq.empty,
        None,
        None,
        Seq.empty
      )

      Json.fromJson[PAYERegistration](json) shouldBe JsSuccess(tstPAYERegistration)
    }

    "fail from json without registrationID" in {
      val json = Json.parse(
        s"""
           |{
           |  "transactionID" : "NNASD9789F",
           |  "internalID" : "09876",
           |  "formCreationTimestamp":"2016-05-31",
           |  "status" : "draft",
           |  "companyDetails":
           |    {
           |      "companyName":"Test Company",
           |      "tradingName":"Test Trading Name",
           |      "roAddress": {
           |        "line1":"14 St Test Walk",
           |        "line2":"Testley",
           |        "line3":"Testford",
           |        "line4":"Testshire",
           |        "postCode":"TE1 1ST",
           |        "country":"UK"
           |      },
           |      "ppobAddress": {
           |        "line1":"15 St Walk",
           |        "line2":"Testley",
           |        "line3":"Testford",
           |        "line4":"Testshire",
           |        "postCode":"TE4 1ST",
           |        "country":"UK"
           |      },
           |      "businessContactDetails": {
           |        "email":"email@test.co.uk",
           |        "phoneNumber":"999",
           |        "mobileNumber":"00000"
           |      }
           |    },
           |  "directors": [],
           |  "sicCodes": []
           |}
        """.stripMargin)

      val result = Json.fromJson[PAYERegistration](json)
      shouldHaveErrors(result, JsPath() \ "registrationID", Seq(ValidationError("error.path.missing")))
    }

    "fail from json without transactionID" in {
      val json = Json.parse(
        s"""
           |{
           |  "registrationID":"12345",
           |  "internalID" : "09876",
           |  "formCreationTimestamp":"2016-05-31",
           |  "status" : "draft",
           |  "companyDetails":
           |    {
           |      "companyName":"Test Company",
           |      "tradingName":"Test Trading Name",
           |      "roAddress": {
           |        "line1":"14 St Test Walk",
           |        "line2":"Testley",
           |        "line3":"Testford",
           |        "line4":"Testshire",
           |        "postCode":"TE1 1ST",
           |        "country":"UK"
           |      },
           |      "ppobAddress": {
           |        "line1":"15 St Walk",
           |        "line2":"Testley",
           |        "line3":"Testford",
           |        "line4":"Testshire",
           |        "postCode":"TE4 1ST",
           |        "country":"UK"
           |      },
           |      "businessContactDetails": {
           |        "email":"email@test.co.uk",
           |        "phoneNumber":"999",
           |        "mobileNumber":"00000"
           |      }
           |    },
           |  "directors": [],
           |  "sicCodes": []
           |}
        """.stripMargin)

      val result = Json.fromJson[PAYERegistration](json)
      shouldHaveErrors(result, JsPath() \ "transactionID", Seq(ValidationError("error.path.missing")))
    }

    "fail if the status isn't one of the pre defined PAYE statuses" in {
      val json = Json.parse(
        s"""
           |{
           |  "registrationID":"12345",
           |  "transactionID" : "NNASD9789F",
           |  "internalID" : "09876",
           |  "formCreationTimestamp":"2016-05-31",
           |  "status" : "INVALID_STATUS",
           |  "companyDetails":
           |    {
           |      "companyName":"Test Company",
           |      "tradingName":"Test Trading Name",
           |      "roAddress": {
           |        "line1":"14 St Test Walk",
           |        "line2":"Testley",
           |        "line3":"Testford",
           |        "line4":"Testshire",
           |        "postCode":"TE1 1ST",
           |        "country":"UK"
           |      },
           |      "ppobAddress": {
           |        "line1":"15 St Walk",
           |        "line2":"Testley",
           |        "line3":"Testford",
           |        "line4":"Testshire",
           |        "postCode":"TE4 1ST",
           |        "country":"UK"
           |      },
           |      "businessContactDetails": {
           |        "email":"email@test.co.uk",
           |        "phoneNumber":"999",
           |        "mobileNumber":"00000"
           |      }
           |    },
           |  "directors": [],
           |  "sicCodes": []
           |}
        """.stripMargin)

      val result = Json.fromJson[PAYERegistration](json)
      shouldHaveErrors(result, JsPath() \ "status", Seq(ValidationError("error.expected.validenumvalue")))
    }
  }
}
