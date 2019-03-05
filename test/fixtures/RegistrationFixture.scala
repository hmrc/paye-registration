/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.{LocalDateTime, ZoneOffset, ZonedDateTime}

import enums.PAYEStatus
import models._

trait RegistrationFixture {

  val validCompanyDetails = CompanyDetails(
    companyName = "Test Company Name",
    tradingName = Some("Test Trading Name"),
    Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK")),
    Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), None),
    DigitalContactDetails(Some("test@email.com"), Some("0123459999"), Some("5432109999"))
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
    SICCode(code = Some("123"), description = Some("consulting")),
    SICCode(code = None, description = Some("something"))
  )

  val validPAYEContact = PAYEContact(
    contactDetails = PAYEContactDetails(
      name = "Toto Tata",
      digitalContactDetails = DigitalContactDetails(
        Some("test@email.com"),
        Some("0123459999"),
        Some("5432109999")
      )
    ),
    correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), Some("TE4 1ST"))
  )

  val zDtNow = ZonedDateTime.of(LocalDateTime.of(2017,1,10,1,2,1),ZoneOffset.UTC)


  val validRegistration = PAYERegistration(
    registrationID = "AC187651",
    transactionID = "NNASD9789F",
    internalID = "09876",
    acknowledgementReference = None,
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "20161021-16:00:00",
    status = PAYEStatus.draft,
    completionCapacity = Some("Director"),
    companyDetails = Some(validCompanyDetails),
    directors = validDirectors,
    payeContact = Some(validPAYEContact),
    sicCodes = validSICCodes,
    lastUpdate = "2017-05-09T07:58:35Z",
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(zDtNow)
  )
}