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

package audit

import java.time.LocalDate

import models.Address
import models.submission.{DESBusinessContact, DESCompanyDetails, DESCompletionCapacity, DESDirector, DESEmployment, DESPAYEContact, DESSICCode, DESSubmission, DESSubmissionModel}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.test.UnitSpec

class DesSubmissionAuditEventDetailSpec extends UnitSpec {

  "DesSubmissionAuditEventDetail" should {

    val externalUserId = "Ext-123456789"
    val authProviderId = "apid001"
    val regId = "123456789"
    val desSubmissionState = "partial"

    "construct full json as per definition" in {
      val validDESCompanyDetails = DESCompanyDetails(
        companyName = "Test Company Name",
        tradingName = Some("Test Trading Name"),
        ppob = Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK")),
        regAddress = Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), Some("UK"))
      )

      val validDESDirectors = Seq(
        DESDirector(
          forename = Some("Thierry"),
          otherForenames = Some("Dominique"),
          surname = Some("Henry"),
          title = Some("Sir"),
          nino = Some("SR123456C")
        ),
        DESDirector(
          forename = Some("David"),
          otherForenames = Some("Jesus"),
          surname = Some("Trezeguet"),
          title = Some("Mr"),
          nino = Some("SR000009C")
        )
      )

      val validDESPAYEContact = DESPAYEContact(
        name = "Toto Tata",
        email = Some("test@email.com"),
        tel = Some("012345"),
        mobile = Some("543210"),
        correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK"))
      )

      val validDESBusinessContact = DESBusinessContact(
        email = Some("test@email.com"),
        tel = Some("012345"),
        mobile = Some("543210")
      )

      val validDESSICCodes = Seq(
        DESSICCode(code = None, description = Some("consulting"))
      )

      val validDESEmployment = DESEmployment(
        employees = true,
        ocpn = Some(true),
        cis = true,
        firstPaymentDate = LocalDate.of(2016, 12, 20)
      )

      val validPartialDESSubmissionModel = DESSubmissionModel(
        acknowledgementReference = "ackRef",
        crn = None,
        company = validDESCompanyDetails,
        directors = validDESDirectors,
        payeContact = validDESPAYEContact,
        businessContact = validDESBusinessContact,
        sicCodes = validDESSICCodes,
        employment = validDESEmployment,
        completionCapacity = DESCompletionCapacity("other", Some("friend"))
      )

      val expected = Json.parse(
        s"""
          |{
          |   "externalId": "$externalUserId",
          |   "authProviderId": "$authProviderId",
          |   "journeyId": "$regId",
          |   "acknowledgementReference": "ackRef",
          |   "desSubmissionState": "$desSubmissionState",
          |   "registrationMetadata": {
          |     "businessType": "Limited company",
          |     "completionCapacity": "other",
          |     "completionCapacityOther": "friend"
          |   },
          |   "paye": {
          |     "companyDetails" : {
          |       "companyName" : "Test Company Name",
          |       "tradingName" : "Test Trading Name",
          |       "ppobAddress" : {
          |         "line1" : "15 St Walk",
          |         "line2" : "Testley",
          |         "line3" : "Testford",
          |         "line4" : "Testshire",
          |         "postCode" : "TE4 1ST",
          |         "country" : "UK"
          |       },
          |       "registeredOfficeAddress" : {
          |         "line1" : "14 St Test Walk",
          |         "line2" : "Testley",
          |         "line3" : "Testford",
          |         "line4" : "Testshire",
          |         "postCode" : "TE1 1ST",
          |         "country" : "UK"
          |       }
          |     },
          |     "directors" : [
          |       {
          |         "forename" : "Thierry",
          |         "surname" : "Henry",
          |         "otherForenames" : "Dominique",
          |         "title" : "Sir",
          |         "nino" : "SR123456C"
          |       }, {
          |         "forename" : "David",
          |         "surname" : "Trezeguet",
          |         "otherForenames" : "Jesus",
          |         "title" : "Mr",
          |         "nino" : "SR000009C"
          |       }
          |     ],
          |     "payeContact" : {
          |       "name" : "Toto Tata",
          |       "email" : "test@email.com",
          |       "tel" : "012345",
          |       "mobile" : "543210",
          |       "correspondenceAddress" : {
          |         "line1" : "19 St Walk",
          |         "line2" : "Testley CA",
          |         "line3" : "Testford",
          |         "line4" : "Testshire",
          |         "postCode" : "TE4 1ST",
          |         "country" : "UK"
          |       }
          |     },
          |     "businessContactDetails" : {
          |       "email" : "test@email.com",
          |       "phoneNumber" : "012345",
          |       "mobileNumber" : "543210"
          |     },
          |     "sicCodes" : [ {
          |       "description" : "consulting"
          |     } ],
          |     "employment" : {
          |       "employees" : true,
          |       "ocpn" : true,
          |       "cis" : true,
          |       "firstPaymentDate" : "2016-12-20"
          |     }
          |   }
          |}
        """.stripMargin)

      val testModel = DesSubmissionAuditEventDetail(
        externalUserId,
        authProviderId,
        regId,
        desSubmissionState,
        Json.toJson[DESSubmission](validPartialDESSubmissionModel).as[JsObject]
      )
      Json.toJson(testModel)(DesSubmissionAuditEventDetail.writes) shouldBe expected
    }
  }
}

