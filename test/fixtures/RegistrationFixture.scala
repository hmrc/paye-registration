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

import models.{CompanyDetails, Employment, PAYERegistration}
import java.time.LocalDate

trait RegistrationFixture {

  val validCompanyDetails = CompanyDetails(
    crn = None,
    companyName = "Test Company Name",
    tradingName = Some("Test Trading Name")
  )

  val validEmployment = Employment(
    employees = true,
    companyPension = Some(true),
    subcontractors = true,
    firstPaymentDate = LocalDate.of(2016, 12, 20)
  )

  val validRegistration = PAYERegistration(
    registrationID = "AC187651",
    internalID = "09876",
    formCreationTimestamp = "20161021-16:00:00",
    companyDetails = Some(validCompanyDetails),
    employment = Some(validEmployment)
  )

}
