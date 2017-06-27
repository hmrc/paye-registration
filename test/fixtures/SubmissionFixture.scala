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

package fixtures

import java.time.LocalDate

import enums.IncorporationStatus
import models.{Address, CompanyDetails, DigitalContactDetails, Director, Employment, Name, SICCode}
import models.incorporation.IncorpStatusUpdate
import models.submission._

trait SubmissionFixture {
  val validCompanyDetails = CompanyDetails(
    companyName = "Test Company Name",
    tradingName = Some("Test Trading Name"),
    Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), Some("UK")),
    Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK")),
    DigitalContactDetails(Some("test@email.com"), Some("012345"), Some("543210"))
  )

  val validEmployment = Employment(
    employees = true,
    companyPension = Some(true),
    subcontractors = true,
    firstPaymentDate = LocalDate.of(2016, 12, 20)
  )

  val validDESCompletionCapacity = DESCompletionCapacity(
    capacity = "other",
    otherCapacity = Some("friend")
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

  val validSICCodes = Seq(
    SICCode(code = None, description = Some("consulting"))
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
    companiesHouseCompanyName = "TESTLTD",
    nameOfBusiness = Some("TEST Business"),
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

  val validTopUpDESSubmissionModel = TopUpDESSubmission(
    acknowledgementReference = "ackRef",
    status = "accepted",
    crn = Some("123456")
  )

  val incorpStatusUpdate = IncorpStatusUpdate(transactionId = "NNASD9789F",
                                     status = IncorporationStatus.accepted,
                                     crn = Some("123456"),
                                     incorporationDate = Some(LocalDate.of(2000, 12, 12)),
                                     description = None,
                                     timestamp = LocalDate.of(2017, 12, 21))
}
