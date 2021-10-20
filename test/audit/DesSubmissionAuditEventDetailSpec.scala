/*
 * Copyright 2021 HM Revenue & Customs
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

import enums.Employing
import helpers.PAYERegSpec
import models._
import models.submission._
import play.api.libs.json.{JsObject, Json}

import java.time.LocalDate

class DesSubmissionAuditEventDetailSpec extends PAYERegSpec {

  "DesSubmissionAuditEventDetail" should {

    val externalId = "Ext-123456789"
    val authProviderId = "apid001"
    val regId = "123456789"
    val desSubmissionState = "partial"

    "construct full json as per definition" in {
      val validCompanyDetails = CompanyDetails(
        companyName = "Test Company Name",
        tradingName = Some("Test Trading Name"),
        Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), Some("UK")),
        Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK")),
        DigitalContactDetails(Some("test@email.com"), Some("012345"), Some("543210"))
      )

      val validDirectors = Seq(
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
      )

      val validEmployment = EmploymentInfo(
        employees = Employing.alreadyEmploying,
        firstPaymentDate = LocalDate.of(2016, 12, 20),
        construction = true,
        subcontractors = true,
        companyPension = Some(true)
      )

      val validDESCompletionCapacity = DESCompletionCapacity(
        capacity = "other",
        otherCapacity = Some("friend")
      )

      val validDESMetaData = DESMetaData(
        sessionId = "session-123",
        credId = "cred-123",
        language = "en",
        submissionTs = "2017-05-03T12:51:42.076",
        completionCapacity = validDESCompletionCapacity
      )

      val validDESLimitedCompanyWithoutCRN = DESLimitedCompany(
        companyUTR = None,
        companiesHouseCompanyName = "Test Company Name",
        nameOfBusiness = Some("Test Trading Name"),
        businessAddress = validCompanyDetails.ppobAddress,
        businessContactDetails = validCompanyDetails.businessContactDetails,
        natureOfBusiness = "consulting",
        crn = None,
        directors = validDirectors,
        registeredOfficeAddress = validCompanyDetails.roAddress,
        operatingOccPensionScheme = validEmployment.companyPension
      )

      val validDESEmployingPeople = DESEmployingPeople(
        dateOfFirstEXBForEmployees = LocalDate.of(2016, 12, 20),
        numberOfEmployeesExpectedThisYear = "1",
        engageSubcontractors = true,
        correspondenceName = "Toto Tata",
        correspondenceContactDetails = DigitalContactDetails(
          Some("test@email.com"),
          Some("012345"),
          Some("543210")
        ),
        payeCorrespondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK"))
      )

      val validPartialDESSubmissionModel = DESSubmission(
        acknowledgementReference = "ackRef",
        metaData = validDESMetaData,
        limitedCompany = validDESLimitedCompanyWithoutCRN,
        employingPeople = validDESEmployingPeople
      )

      val expected = Json.parse(
        s"""
          |{
          |   "externalId": "$externalId",
          |   "authProviderId": "$authProviderId",
          |   "journeyId": "$regId",
          |   "desSubmissionState": "$desSubmissionState",
          |   "acknowledgementReference": "ackRef",
          |   "metaData": {
          |     "businessType": "Limited company",
          |     "sessionID": "session-123",
          |     "credentialID": "cred-123",
          |     "formCreationTimestamp": "2017-05-03T12:51:42.076",
          |     "language": "en",
          |     "submissionFromAgent": false,
          |     "completionCapacity": "Other",
          |     "completionCapacityOther": "friend",
          |     "declareAccurateAndComplete": true
          |   },
          |   "payAsYouEarn": {
          |     "limitedCompany" : {
          |       "companiesHouseCompanyName" : "Test Company Name",
          |       "nameOfBusiness" : "Test Trading Name",
          |       "businessAddress" : {
          |         "addressLine1" : "15 St Walk",
          |         "addressLine2" : "Testley",
          |         "addressLine3" : "Testford",
          |         "addressLine4" : "Testshire",
          |         "postcode" : "TE4 1ST",
          |         "country" : "UK"
          |       },
          |       "businessContactDetails": {
          |         "email" : "test@email.com",
          |         "phoneNumber" : "012345",
          |         "mobileNumber" : "543210"
          |       },
          |       "natureOfBusiness": "consulting",
          |       "directors" : [
          |         {
          |           "directorName": {
          |             "firstName" : "Thierry",
          |             "lastName" : "Henry",
          |             "middleName" : "Dominique",
          |             "title" : "Sir"
          |           },
          |           "directorNINO" : "SR123456C"
          |         }, {
          |           "directorName": {
          |             "firstName" : "David",
          |             "lastName" : "Trezeguet",
          |             "middleName" : "Jesus",
          |             "title" : "Mr"
          |           },
          |           "directorNINO" : "SR000009C"
          |         }
          |       ],
          |       "registeredOfficeAddress" : {
          |         "addressLine1" : "14 St Test Walk",
          |         "addressLine2" : "Testley",
          |         "addressLine3" : "Testford",
          |         "addressLine4" : "Testshire",
          |         "postcode" : "TE1 1ST",
          |         "country" : "UK"
          |       },
          |       "operatingOccPensionScheme": true
          |     },
          |     "employingPeople": {
          |       "dateOfFirstEXBForEmployees": "2016-12-20",
          |       "numberOfEmployeesExpectedThisYear": "1",
          |       "engageSubcontractors": true,
          |       "correspondenceName": "Toto Tata",
          |       "correspondenceContactDetails": {
          |         "email": "test@email.com",
          |         "phoneNumber": "012345",
          |         "mobileNumber": "543210"
          |       },
          |       "payeCorrespondenceAddress": {
          |         "addressLine1" : "19 St Walk",
          |         "addressLine2" : "Testley CA",
          |         "addressLine3" : "Testford",
          |         "addressLine4" : "Testshire",
          |         "postcode" : "TE4 1ST",
          |         "country" : "UK"
          |       }
          |     }
          |   }
          |}
        """.stripMargin)

      val testModel = DesSubmissionAuditEventDetail(
        externalId,
        authProviderId,
        regId,
        None,
        desSubmissionState,
        Json.toJson[DESSubmission](validPartialDESSubmissionModel).as[JsObject],
        Map.empty
      )
      Json.toJson(testModel)(DesSubmissionAuditEventDetail.writes) shouldBe expected
    }
  }
}
