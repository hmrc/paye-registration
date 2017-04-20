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

import models.Address
import models.incorporation.IncorpStatusUpdate
import models.submission._

trait SubmissionFixture {

  val validDESCompanyDetails = DESCompanyDetails(
    companyName = "Test Company Name",
    tradingName = Some("Test Trading Name"),
    ppob = Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK")),
    regAddress = Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), Some("UK"))
  )

  val validDESBusinessContact = DESBusinessContact(
    email = Some("test@email.com"),
    tel = Some("012345"),
    mobile = Some("543210")
  )

  val validDESEmployment = DESEmployment(
    employees = true,
    ocpn = Some(true),
    cis = true,
    firstPaymentDate = LocalDate.of(2016, 12, 20)
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

  val validDESSICCodes = Seq(
    DESSICCode(code = None, description = Some("consulting"))
  )

  val validDESPAYEContact = DESPAYEContact(
    name = "Toto Tata",
    email = Some("test@email.com"),
    tel = Some("012345"),
    mobile = Some("543210"),
    correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK"))
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
    completionCapacity = DESCompletionCapacity("director", None)
  )

  val validTopUpDESSubmissionModel = TopUpDESSubmission(
    acknowledgementReference = "ackRef",
    status = "accepted",
    crn = Some("123456")
  )

  val incorpStatusUpdate = IncorpStatusUpdate(transactionId = "NNASD9789F",
                                     status = "accepted",
                                     crn = Some("123456"),
                                     incorporationDate = Some(LocalDate.of(2000, 12, 12)),
                                     description = None,
                                     timestamp = "2017-12-21T10:13:09.429Z")
}
