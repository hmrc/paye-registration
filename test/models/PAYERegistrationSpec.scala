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


import java.time.{LocalDate, LocalDateTime, ZoneOffset, ZonedDateTime}

import auth.CryptoSCRSImpl
import enums.{Employing, PAYEStatus}
import helpers.PAYERegSpec
import models.validation.{APIValidation, MongoValidation}
import play.api.Configuration
import play.api.libs.json.{JsPath, JsSuccess, Json, JsonValidationError}
import uk.gov.hmrc.play.test.UnitSpec
import utils.SystemDate

class PAYERegistrationSpec extends UnitSpec with JsonFormatValidation with PAYERegSpec {
  val cryptoSCRS = mockCrypto
  val timestamp = "2017-05-09T07:58:35.000Z"

  val zDtNow = ZonedDateTime.of(LocalDateTime.of(2000,1,20,16,0),ZoneOffset.UTC)
  "Creating a PAYERegistration model from Json" should {
    "complete successfully from full Json that has the old eligibility block in" in {

      val date = LocalDate.of(2016, 12, 20)

      val json = Json.parse(
        s"""
           |{
           |  "registrationID":"12345",
           |  "transactionID" : "NNASD9789F",
           |  "internalID" : "09876",
           |  "formCreationTimestamp":"2016-05-31",
           |  "registrationConfirmation" : {
           |    "empRef":"testEmpRef",
           |    "timestamp":"2017-01-01T12:00:00Z",
           |    "status":"testStatus"
           |  },
           |  "eligibility" : {
           |    "companyEligibility" : false,
           |    "directorEligibility" : false
           |  },
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
           |        "auditRef":"testAudit"
           |      },
           |      "ppobAddress": {
           |        "line1":"15 St Walk",
           |        "line2":"Testley",
           |        "line3":"Testford",
           |        "line4":"Testshire",
           |        "country":"UK"
           |      },
           |      "businessContactDetails": {
           |        "email":"email@test.co.uk",
           |        "phoneNumber":"9999999999",
           |        "mobileNumber":"0000000000"
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
           |        "phoneNumber": "6549999999",
           |        "mobileNumber": "1234599999"
           |      }
           |    },
           |    "correspondenceAddress": {
           |      "line1":"19 St Walk",
           |      "line2":"Testley CA",
           |      "line3":"Testford",
           |      "line4":"Testshire",
           |      "country":"UK"
           |    }
           |  },
           |  "employmentInfo": {
           |    "employees": "notEmploying",
           |    "firstPaymentDate": "${SystemDate.getSystemDate.toLocalDate}",
           |    "construction": true,
           |    "subcontractors": true
           |  },
           |  "sicCodes": [
           |    {
           |      "code":"666",
           |      "description":"demolition"
           |    },
           |    {
           |      "description":"laundring"
           |    }
           |  ],
           |  "lastUpdate": "$timestamp",
           |  "lastAction": "2000-01-20T16:00:00Z"
           |}
        """.stripMargin)

      val tstPAYERegistration = PAYERegistration(
        registrationID = "12345",
        transactionID = "NNASD9789F",
        internalID = "09876",
        acknowledgementReference = None,
        crn = None,
        formCreationTimestamp = "2016-05-31",
        registrationConfirmation = Some(EmpRefNotification(
          empRef = Some("testEmpRef"),
          timestamp = "2017-01-01T12:00:00Z",
          status = "testStatus"
        )),
        status = PAYEStatus.draft,
        completionCapacity = Some("Director"),
        companyDetails = Some(
          CompanyDetails(
            companyName = "Test Company",
            tradingName = Some("Test Trading Name"),
            Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), None, Some("testAudit")),
            Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK")),
            DigitalContactDetails(Some("email@test.co.uk"), Some("9999999999"), Some("0000000000"))
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
                Some("6549999999"),
                Some("1234599999")
              )
            ),
            correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), None, Some("UK"))
          )
        ),

        sicCodes = Seq(
          SICCode(code = Some("666"), description = Some("demolition")),
          SICCode(code = None, description = Some("laundring"))
        ),
        lastUpdate = timestamp,
        partialSubmissionTimestamp = None,
        fullSubmissionTimestamp = None,
        acknowledgedTimestamp = None,
        lastAction = Some(zDtNow),
        employmentInfo = Some(EmploymentInfo(Employing.notEmploying,SystemDate.getSystemDate.toLocalDate,true, true,None))
      )

      Json.fromJson[PAYERegistration](json)(PAYERegistration.format(APIValidation, cryptoSCRS)) shouldBe JsSuccess(tstPAYERegistration)
    }

    "complete successfully from full Json that doesn't include an eligibility block which is the as-is position" in {

      val date = LocalDate.of(2016, 12, 20)

      val json = Json.parse(
        s"""
           |{
           |  "registrationID":"12345",
           |  "transactionID" : "NNASD9789F",
           |  "internalID" : "09876",
           |  "formCreationTimestamp":"2016-05-31",
           |  "registrationConfirmation" : {
           |    "empRef":"testEmpRef",
           |    "timestamp":"2017-01-01T12:00:00Z",
           |    "status":"testStatus"
           |  },
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
           |        "auditRef":"testAudit"
           |      },
           |      "ppobAddress": {
           |        "line1":"15 St Walk",
           |        "line2":"Testley",
           |        "line3":"Testford",
           |        "line4":"Testshire",
           |        "country":"UK"
           |      },
           |      "businessContactDetails": {
           |        "email":"email@test.co.uk",
           |        "phoneNumber":"9999999999",
           |        "mobileNumber":"0000000000"
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
           |        "phoneNumber": "6549999999",
           |        "mobileNumber": "1234599999"
           |      }
           |    },
           |    "correspondenceAddress": {
           |      "line1":"19 St Walk",
           |      "line2":"Testley CA",
           |      "line3":"Testford",
           |      "line4":"Testshire",
           |      "country":"UK"
           |    }
           |  },
           |  "employmentInfo": {
           |    "employees": "notEmploying",
           |    "firstPaymentDate": "${SystemDate.getSystemDate.toLocalDate}",
           |    "construction": true,
           |    "subcontractors": true
           |  },
           |  "sicCodes": [
           |    {
           |      "code":"666",
           |      "description":"demolition"
           |    },
           |    {
           |      "description":"laundring"
           |    }
           |  ],
           |  "lastUpdate": "$timestamp",
           |  "lastAction": "2000-01-20T16:00:00Z"
           |}
        """.stripMargin)

      val tstPAYERegistration = PAYERegistration(
        registrationID = "12345",
        transactionID = "NNASD9789F",
        internalID = "09876",
        acknowledgementReference = None,
        crn = None,
        formCreationTimestamp = "2016-05-31",
        registrationConfirmation = Some(EmpRefNotification(
          empRef = Some("testEmpRef"),
          timestamp = "2017-01-01T12:00:00Z",
          status = "testStatus"
        )),
        status = PAYEStatus.draft,
        completionCapacity = Some("Director"),
        companyDetails = Some(
          CompanyDetails(
            companyName = "Test Company",
            tradingName = Some("Test Trading Name"),
            Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), None, Some("testAudit")),
            Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK")),
            DigitalContactDetails(Some("email@test.co.uk"), Some("9999999999"), Some("0000000000"))
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
                Some("6549999999"),
                Some("1234599999")
              )
            ),
            correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), None, Some("UK"))
          )
        ),

        sicCodes = Seq(
          SICCode(code = Some("666"), description = Some("demolition")),
          SICCode(code = None, description = Some("laundring"))
        ),
        lastUpdate = timestamp,
        partialSubmissionTimestamp = None,
        fullSubmissionTimestamp = None,
        acknowledgedTimestamp = None,
        lastAction = Some(zDtNow),
        employmentInfo = Some(EmploymentInfo(Employing.notEmploying,SystemDate.getSystemDate.toLocalDate,true, true,None))
      )

      Json.fromJson[PAYERegistration](json)(PAYERegistration.format(APIValidation, cryptoSCRS)) shouldBe JsSuccess(tstPAYERegistration)
    }

    "complete successfully from json when lastAction is in mongo Format (using mongo reads)" in {
      val json1 = Json.parse(
        """
           |{
           |  "registrationID":"12345",
           |  "transactionID" : "NNASD9789F",
           |  "internalID" : "09876",
           |  "formCreationTimestamp":"2016-05-31",
           |  "eligibility" : {
           |    "companyEligibility" : false,
           |    "directorEligibility" : false
           |  },
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
           |        "auditRef":"testAudit"
           |      },
           |      "ppobAddress": {
           |        "line1":"15 St Walk",
           |        "line2":"Testley",
           |        "line3":"Testford",
           |        "line4":"Testshire",
           |        "country":"UK"
           |      },
           |      "businessContactDetails": {
           |        "email":"email@test.co.uk",
           |        "phoneNumber":"9999999999",
           |        "mobileNumber":"0000000000"
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
           |        "phoneNumber": "6549999999",
           |        "mobileNumber": "1234599999"
           |      }
           |    },
           |    "correspondenceAddress": {
           |      "line1":"19 St Walk",
           |      "line2":"Testley CA",
           |      "line3":"Testford",
           |      "line4":"Testshire",
           |      "country":"UK"
           |    }
           |  },
           |  "employmentInfo": {
           |  "employees": "alreadyEmploying",
           |    "firstPaymentDate": "2016-12-20",
           |    "construction": true,
           |    "subcontractors": true,
           |    "companyPension": true
           |  },
           |  "sicCodes": [
           |    {
           |      "code":"666",
           |      "description":"demolition"
           |    },
           |    {
           |      "description":"laundring"
           |    }
           |  ],
           |  "lastUpdate": "2017-05-09T07:58:35.000Z",
           |  "lastAction": {"$date": 1483232461000 }
           |}
        """.stripMargin)

      Json.fromJson[PAYERegistration](json1)(PAYERegistration.format(MongoValidation, cryptoSCRS)).map(s => s.lastAction).get shouldBe Some(ZonedDateTime.of(LocalDateTime.of(2017,1,1,1,1,1),ZoneOffset.UTC))
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
           |  "sicCodes" : [],
           |  "lastUpdate" : "$timestamp"
           |}
        """.stripMargin)

      val tstPAYERegistration = PAYERegistration(
        registrationID = "12345",
        transactionID = "NNASD9789F",
        internalID = "09876",
        acknowledgementReference = None,
        crn = None,
        registrationConfirmation = None,
        formCreationTimestamp = "2016-05-31",
        status = PAYEStatus.draft,
        completionCapacity = None,
        companyDetails = None,
        directors = Seq.empty,
        payeContact = None,
        sicCodes = Seq.empty,
        lastUpdate = timestamp,
        partialSubmissionTimestamp = None,
        fullSubmissionTimestamp = None,
        acknowledgedTimestamp = None,
        lastAction = None,
        employmentInfo = None
      )
      implicit val f = PAYERegistration.format(APIValidation, new CryptoSCRSImpl(Configuration("json.encryption.key" -> "MTIzNDU2Nzg5MDEyMzQ1Ng==")))
      Json.fromJson[PAYERegistration](json) shouldBe JsSuccess(tstPAYERegistration)
    }

    "fail from json without registrationID" in {
      val json = Json.parse(
        s"""
           |{
           |  "transactionID" : "NNASD9789F",
           |  "internalID" : "09876",
           |  "formCreationTimestamp":"2016-05-31",
           |  "eligibility" : {
           |    "companyEligibility" : false,
           |    "directorEligibility" : false
           |  },
           |  "registrationConfirmation": {
           |    "empRef":"testEmpRef",
           |    "timestamp":"2017-01-01T12:00:00Z",
           |    "status":"testStatus"
           |  },
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
           |        "country":"UK"
           |      },
           |      "ppobAddress": {
           |        "line1":"15 St Walk",
           |        "line2":"Testley",
           |        "line3":"Testford",
           |        "line4":"Testshire",
           |        "country":"UK"
           |      },
           |      "businessContactDetails": {
           |        "email":"email@test.co.uk",
           |        "phoneNumber":"9999999999",
           |        "mobileNumber":"0000000000"
           |      }
           |    },
           |  "directors": [],
           |  "sicCodes": [],
           |  "lastUpdate": "$timestamp"
           |}
        """.stripMargin)
      implicit val f = PAYERegistration.format(APIValidation, new CryptoSCRSImpl(Configuration("json.encryption.key" -> "MTIzNDU2Nzg5MDEyMzQ1Ng==")))
      val result = Json.fromJson[PAYERegistration](json)
      shouldHaveErrors(result, JsPath() \ "registrationID", Seq(JsonValidationError("error.path.missing")))
    }

    "fail from json without transactionID" in {
      val json = Json.parse(
        s"""
           |{
           |  "registrationID":"12345",
           |  "internalID" : "09876",
           |  "formCreationTimestamp":"2016-05-31",
           |  "eligibility" : {
           |    "companyEligibility" : false,
           |    "directorEligibility" : false
           |  },
           |  "registrationConfirmation": {
           |    "empRef":"testEmpRef",
           |    "timestamp":"2017-01-01T12:00:00Z",
           |    "status":"testStatus"
           |  },
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
           |        "country":"UK"
           |      },
           |      "ppobAddress": {
           |        "line1":"15 St Walk",
           |        "line2":"Testley",
           |        "line3":"Testford",
           |        "line4":"Testshire",
           |        "country":"UK"
           |      },
           |      "businessContactDetails": {
           |        "email":"email@test.co.uk",
           |        "phoneNumber":"9999999999",
           |        "mobileNumber":"0000000000"
           |      }
           |    },
           |  "directors": [],
           |  "sicCodes": [],
           |  "lastUpdate": "$timestamp"
           |}
        """.stripMargin)
      implicit val f = PAYERegistration.format(APIValidation, new CryptoSCRSImpl(Configuration("json.encryption.key" -> "MTIzNDU2Nzg5MDEyMzQ1Ng==")))
      val result = Json.fromJson[PAYERegistration](json)
      shouldHaveErrors(result, JsPath() \ "transactionID", Seq(JsonValidationError("error.path.missing")))
    }

    "fail if the status isn't one of the pre defined PAYE statuses" in {
      val json = Json.parse(
        s"""
           |{
           |  "registrationID":"12345",
           |  "transactionID" : "NNASD9789F",
           |  "internalID" : "09876",
           |  "formCreationTimestamp":"2016-05-31",
           |  "eligibility" : {
           |    "companyEligibility" : true,
           |    "directorEligibility" : true
           |  },
           |  "registrationConfirmation": {
           |    "empRef":"testEmpRef",
           |    "timestamp":"2017-01-01T12:00:00Z",
           |    "status":"testStatus"
           |  },
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
           |        "country":"UK"
           |      },
           |      "ppobAddress": {
           |        "line1":"15 St Walk",
           |        "line2":"Testley",
           |        "line3":"Testford",
           |        "line4":"Testshire",
           |        "country":"UK"
           |      },
           |      "businessContactDetails": {
           |        "email":"email@test.co.uk",
           |        "phoneNumber":"9999999999",
           |        "mobileNumber":"0000000000"
           |      }
           |    },
           |  "directors": [],
           |  "sicCodes": [],
           |  "lastUpdate": "$timestamp"
           |}
        """.stripMargin)
      implicit val f = PAYERegistration.format(APIValidation, new CryptoSCRSImpl(Configuration("json.encryption.key" -> "MTIzNDU2Nzg5MDEyMzQ1Ng==")))
      val result = Json.fromJson[PAYERegistration](json)
      shouldHaveErrors(result, JsPath() \ "status", Seq(JsonValidationError("error.expected.validenumvalue")))
    }

    "fail from json without lastUpdate" in {
      val json = Json.parse(
        s"""
           |{
           |  "registrationID":"12345",
           |  "transactionID" : "NNASD9789F",
           |  "internalID" : "09876",
           |  "formCreationTimestamp":"2016-05-31",
           |  "eligibility" : {
           |    "companyEligibility" : false,
           |    "directorEligibility" : false
           |  },
           |  "registrationConfirmation": {
           |    "empRef":"testEmpRef",
           |    "timestamp":"2017-01-01T12:00:00Z",
           |    "status":"testStatus"
           |  },
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
           |        "country":"UK"
           |      },
           |      "ppobAddress": {
           |        "line1":"15 St Walk",
           |        "line2":"Testley",
           |        "line3":"Testford",
           |        "line4":"Testshire",
           |        "country":"UK"
           |      },
           |      "businessContactDetails": {
           |        "email":"email@test.co.uk",
           |        "phoneNumber":"9999 999 99 9",
           |        "mobileNumber":"00000000000000000000"
           |      }
           |    },
           |  "directors": [],
           |  "sicCodes": []
           |}
        """.stripMargin)
      implicit val f = PAYERegistration.format(APIValidation, new CryptoSCRSImpl(Configuration("json.encryption.key" -> "MTIzNDU2Nzg5MDEyMzQ1Ng==")))
      val result = Json.fromJson[PAYERegistration](json)
      shouldHaveErrors(result, JsPath() \ "lastUpdate", Seq(JsonValidationError("error.path.missing")))
    }
  }
}