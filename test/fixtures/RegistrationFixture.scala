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

import models._
import java.time.LocalDate

import enums.PAYEStatus

trait RegistrationFixture {

  val validCompanyDetails = CompanyDetails(
    crn = None,
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
        Some("012345"),
        Some("543210")
      )
    ),
    correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK"))
  )

  val validRegistration = PAYERegistration(
    registrationID = "AC187651",
    internalID = "09876",
    formCreationTimestamp = "20161021-16:00:00",
    status = PAYEStatus.draft,
    completionCapacity = Some("Director"),
    companyDetails = Some(validCompanyDetails),
    directors = validDirectors,
    payeContact = Some(validPAYEContact),
    employment = Some(validEmployment),
    validSICCodes
  )

}
