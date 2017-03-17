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

package services

import java.time.LocalDate

import common.exceptions.RegistrationExceptions._
import connectors.DESConnector
import enums.PAYEStatus
import fixtures.RegistrationFixture
import models._
import models.submission._
import testHelpers.PAYERegSpec
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._

import scala.concurrent.Future

class SubmissionServiceSpec extends PAYERegSpec {

  val mockDESConnector = mock[DESConnector]

  class Setup {
    val service = new SubmissionSrv{
      override val sequenceRepository = mockSequenceRepository
      override val registrationRepository = mockRegistrationRepository
      override val desConnector = mockDESConnector
    }
  }


  val validCompanyDetails = CompanyDetails(
    crn = None,
    companyName = "Test Company Name",
    tradingName = Some("Test Trading Name"),
    Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), Some("UK")),
    Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK")),
    DigitalContactDetails(Some("test@email.com"), Some("012345"), Some("543210"))
  )

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

  val validEmployment = Employment(
    employees = true,
    companyPension = Some(true),
    subcontractors = true,
    firstPaymentDate = LocalDate.of(2016, 12, 20)
  )

  val validDESEmployment = DESEmployment(
    employees = true,
    ocpn = Some(true),
    cis = true,
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

  val validSICCodes = Seq(
    SICCode(code = None, description = Some("consulting"))
  )

  val validDESSICCodes = Seq(
    DESSICCode(code = None, description = Some("consulting"))
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

  val validDESPAYEContact = DESPAYEContact(
    name = "Toto Tata",
    email = Some("test@email.com"),
    tel = Some("012345"),
    mobile = Some("543210"),
    correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK"))
  )

  val validRegistration = PAYERegistration(
    registrationID = "regID",
    internalID = "internalID",
    status = PAYEStatus.draft,
    acknowledgementReference = Some("ackRef"),
    formCreationTimestamp = "the year of the rooster",
    companyDetails = Some(validCompanyDetails),
    completionCapacity = Some("director"),
    directors = validDirectors,
    payeContact = Some(validPAYEContact),
    employment = Some(validEmployment),
    sicCodes = validSICCodes
  )

  val validPartialDESSubmissionModel = PartialDESSubmission(
    acknowledgementReference = "ackRef",
    company = validDESCompanyDetails,
    directors = validDESDirectors,
    payeContact = validDESPAYEContact,
    businessContact = validDESBusinessContact,
    sicCodes = validDESSICCodes,
    employment = validDESEmployment,
    completionCapacity = DESCompletionCapacity("director", None)
  )

  "Calling assertOrGenerateAcknowledgementReference" should {
    "return an ack ref if there is one in the registration" in new Setup {
      when(mockRegistrationRepository.retrieveAcknowledgementReference(ArgumentMatchers.anyString()))
          .thenReturn(Future.successful(Some("tstAckRef")))

      await(service.assertOrGenerateAcknowledgementReference("regID")) shouldBe "tstAckRef"
    }

    "generate an ack ref if there isn't one in the registration" in new Setup {
      when(mockRegistrationRepository.retrieveAcknowledgementReference(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(None))
      when(mockSequenceRepository.getNext(ArgumentMatchers.contains("AcknowledgementID")))
        .thenReturn(Future.successful(1234))
      when(mockRegistrationRepository.saveAcknowledgementReference(ArgumentMatchers.anyString(), ArgumentMatchers.contains("1234")))
      .thenReturn(Future.successful("BRPY00000001234"))

      await(service.assertOrGenerateAcknowledgementReference("regID")) shouldBe "BRPY00000001234"
    }
  }

  "Calling build functions with undefined options" should {
    "throw the correct exception for PAYE Contact" in new Setup {
      intercept[PAYEContactNotDefinedException](service.buildDESPAYEContact(None))
    }
    "throw the correct exception for Employment Details" in new Setup {
      intercept[EmploymentDetailsNotDefinedException](service.buildDESEmploymentDetails(None))
    }
    "throw the correct exception for Completion Capacity" in new Setup {
      intercept[CompletionCapacityNotDefinedException](service.buildDESCompletionCapacity(None))
    }
  }

  "Building DES Completion Capacity" should {
    "succeed for agent" in new Setup {
      service.buildDESCompletionCapacity(Some("agent")) shouldBe DESCompletionCapacity("agent", None)
    }
    "succeed for 'DiReCTOR  '" in new Setup {
      service.buildDESCompletionCapacity(Some("DiReCTOR  ")) shouldBe DESCompletionCapacity("director", None)
    }
    "succeed for 'high priestess'" in new Setup {
      service.buildDESCompletionCapacity(Some("high priestess")) shouldBe DESCompletionCapacity("other", Some("high priestess"))
    }
  }

  "Building a partial DES Submission" should {
    "throw the correct error when company details are not present" in new Setup{
      intercept[CompanyDetailsNotDefinedException](service.payeReg2PartialDESSubmission(validRegistration.copy(companyDetails = None)))
    }
    "throw the correct error when acknowledgement reference is not present" in new Setup{
      intercept[AcknowledgementReferenceNotExistsException](service.payeReg2PartialDESSubmission(validRegistration.copy(acknowledgementReference = None)))
    }
    "build a partial" in new Setup{
      service.payeReg2PartialDESSubmission(validRegistration) shouldBe validPartialDESSubmissionModel
    }
  }
}
