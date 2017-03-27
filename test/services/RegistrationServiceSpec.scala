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

import common.exceptions.DBExceptions.MissingRegDocument
import enums.PAYEStatus
import fixtures.RegistrationFixture
import helpers.PAYERegSpec
import models.{CompanyDetails, Director, Employment, PAYEContact, SICCode}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.mockito.Mockito._

import scala.concurrent.Future

class RegistrationServiceSpec extends PAYERegSpec with RegistrationFixture {

  class Setup {
    val service = new RegistrationSrv {
      override val registrationRepository = mockRegistrationRepository
    }
  }

  val regId = "AB123456"

  "Calling newPAYERegistration" should {

    "return a DBDuplicate response when the database already has a PAYERegistration" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.eq(validRegistration.registrationID))).thenReturn(Future.successful(Some(validRegistration)))

      val actual = await(service.createNewPAYERegistration(validRegistration.registrationID, validRegistration.transactionID, validRegistration.internalID))
      actual shouldBe validRegistration
    }

    "return a DBSuccess response when the Registration is correctly inserted into the database" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.contains("AC123456"))).thenReturn(Future.successful(None))
      when(mockRegistrationRepository.createNewRegistration(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.contains("NNASD9789F"), ArgumentMatchers.any[String]())).thenReturn(Future.successful(validRegistration))

      val actual = await(service.createNewPAYERegistration("AC123456", "NNASD9789F", "09876"))
      actual shouldBe validRegistration
    }
  }

  "Calling fetchPAYERegistration" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.eq(regId))).thenReturn(Future.successful(None))

      val actual = await(service.fetchPAYERegistration(regId))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.eq(regId))).thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.fetchPAYERegistration(regId)) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.eq(regId))).thenReturn(Future.successful(Some(validRegistration)))

      val actual = await(service.fetchPAYERegistration(regId))
      actual shouldBe Some(validRegistration)
    }
  }

  "Calling getCompanyDetails" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveCompanyDetails(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(None))

      val actual = await(service.getCompanyDetails(regId))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveCompanyDetails(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getCompanyDetails(regId)) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveCompanyDetails(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(Some(validCompanyDetails)))

      val actual = await(service.getCompanyDetails(regId))
      actual shouldBe validRegistration.companyDetails
    }
  }

  "Calling upsertCompanyDetails" should {

    "return a DBNotFound response when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertCompanyDetails(ArgumentMatchers.eq(regId), ArgumentMatchers.any[CompanyDetails]()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      intercept[MissingRegDocument] { await(service.upsertCompanyDetails(regId, validCompanyDetails)) }
    }

    "return a DBSuccess response when the company details are successfully updated" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.upsertCompanyDetails(ArgumentMatchers.eq(regId), ArgumentMatchers.any[CompanyDetails]()))
        .thenReturn(Future.successful(validCompanyDetails))

      val actual = await(service.upsertCompanyDetails(regId, validCompanyDetails))
      actual shouldBe validCompanyDetails
    }
  }

  "Calling getEmployment" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveEmployment(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(None))

      val actual = await(service.getEmployment(regId))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveEmployment(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getEmployment(regId)) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveEmployment(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(Some(validEmployment)))

      val actual = await(service.getEmployment(regId))
      actual shouldBe validRegistration.employment
    }
  }

  "Calling upsertEmployment" should {

    "return a DBNotFound response when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertEmployment(ArgumentMatchers.eq(regId), ArgumentMatchers.any[Employment]()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      intercept[MissingRegDocument] { await(service.upsertEmployment(regId, validEmployment)) }
    }

    "return a DBSuccess response when the company details are successfully updated" in new Setup {
      when(mockRegistrationRepository.upsertEmployment(ArgumentMatchers.eq(regId), ArgumentMatchers.any[Employment]()))
        .thenReturn(Future.successful(validRegistration))

      await(service.upsertEmployment(regId, validEmployment)) shouldBe validEmployment
      verify(mockRegistrationRepository, times(0)).updateRegistrationStatus(ArgumentMatchers.eq(regId), ArgumentMatchers.any())
    }

    "return a DBSuccess response when the company details are successfully updated from previously invalid" in new Setup {
      when(mockRegistrationRepository.upsertEmployment(ArgumentMatchers.eq(regId), ArgumentMatchers.any[Employment]()))
        .thenReturn(Future.successful(validRegistration.copy(status = PAYEStatus.invalid)))

      when(mockRegistrationRepository.updateRegistrationStatus(ArgumentMatchers.eq(regId), ArgumentMatchers.any[PAYEStatus.Value]()))
        .thenReturn(Future.successful(PAYEStatus.draft))

      val captor = ArgumentCaptor.forClass(classOf[PAYEStatus.Value])

      await(service.upsertEmployment(regId, validEmployment)) shouldBe validEmployment
      verify(mockRegistrationRepository, times(1)).updateRegistrationStatus(ArgumentMatchers.eq(regId), captor.capture())
      captor.getValue shouldBe PAYEStatus.draft
    }

    "set status to INVALID when false is recorded for both Subcontractors and Employees" in new Setup {
      val invalidEmployment = Employment(employees = false, None, subcontractors = false, validEmployment.firstPaymentDate)
      when(mockRegistrationRepository.upsertEmployment(ArgumentMatchers.eq(regId), ArgumentMatchers.any[Employment]()))
        .thenReturn(Future.successful(validRegistration))

      when(mockRegistrationRepository.updateRegistrationStatus(ArgumentMatchers.eq(regId), ArgumentMatchers.any[PAYEStatus.Value]()))
        .thenReturn(Future.successful(PAYEStatus.invalid))

      val captor = ArgumentCaptor.forClass(classOf[PAYEStatus.Value])

      await(service.upsertEmployment(regId, invalidEmployment)) shouldBe invalidEmployment
      verify(mockRegistrationRepository, times(1)).updateRegistrationStatus(ArgumentMatchers.eq(regId), captor.capture())
      captor.getValue shouldBe PAYEStatus.invalid
    }

    "not set status to INVALID when false is recorded for both Subcontractors and Employees if already INVALID" in new Setup {
      val invalidEmployment = Employment(employees = false, None, subcontractors = false, validEmployment.firstPaymentDate)
      when(mockRegistrationRepository.upsertEmployment(ArgumentMatchers.eq(regId), ArgumentMatchers.any[Employment]()))
        .thenReturn(Future.successful(validRegistration.copy(status = PAYEStatus.invalid)))

      val captor = ArgumentCaptor.forClass(classOf[PAYEStatus.Value])

      await(service.upsertEmployment(regId, invalidEmployment)) shouldBe invalidEmployment
      verify(mockRegistrationRepository, times(0)).updateRegistrationStatus(ArgumentMatchers.eq(regId), captor.capture())
    }
  }

  "Calling getDirectors" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveDirectors(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(Seq.empty))

      val actual = await(service.getDirectors(regId))
      actual shouldBe Seq.empty
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveDirectors(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getDirectors(regId)) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveDirectors(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(validDirectors))

      val actual = await(service.getDirectors(regId))
      actual shouldBe validRegistration.directors
    }
  }

  "Calling upsertDirectors" should {

    "return a DBNotFound response when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertDirectors(ArgumentMatchers.eq(regId), ArgumentMatchers.any[Seq[Director]]()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      intercept[MissingRegDocument] { await(service.upsertDirectors(regId, validDirectors)) }
    }

    "return a DBSuccess response when the company details are successfully updated" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.upsertDirectors(ArgumentMatchers.eq(regId), ArgumentMatchers.any[Seq[Director]]()))
        .thenReturn(Future.successful(validDirectors))

      val actual = await(service.upsertDirectors(regId, validDirectors))
      actual shouldBe validDirectors
    }
  }

  "Calling getSICCodes" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveSICCodes(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(Seq.empty))

      val actual = await(service.getSICCodes(regId))
      actual shouldBe Seq.empty
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveSICCodes(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getSICCodes(regId)) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveSICCodes(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(validSICCodes))

      val actual = await(service.getSICCodes(regId))
      actual shouldBe validRegistration.sicCodes
    }
  }

  "Calling upsertSICCodes" should {

    "return a DBNotFound response when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertSICCodes(ArgumentMatchers.eq(regId), ArgumentMatchers.any[Seq[SICCode]]()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      intercept[MissingRegDocument] { await(service.upsertSICCodes(regId, validSICCodes)) }
    }

    "return a DBSuccess response when the company details are successfully updated" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.upsertSICCodes(ArgumentMatchers.eq(regId), ArgumentMatchers.any[Seq[SICCode]]()))
        .thenReturn(Future.successful(validSICCodes))

      val actual = await(service.upsertSICCodes(regId, validSICCodes))
      actual shouldBe validSICCodes
    }
  }

  "Calling getPAYEContact" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrievePAYEContact(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(None))

      val actual = await(service.getPAYEContact(regId))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrievePAYEContact(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getPAYEContact(regId)) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrievePAYEContact(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(Some(validPAYEContact)))

      val actual = await(service.getPAYEContact(regId))
      actual shouldBe validRegistration.payeContact
    }
  }

  "Calling upsertPAYEContact" should {

    "return a DBNotFound response when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertPAYEContact(ArgumentMatchers.eq(regId), ArgumentMatchers.any[PAYEContact]()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      intercept[MissingRegDocument] { await(service.upsertPAYEContact(regId, validPAYEContact)) }
    }

    "return a DBSuccess response when the paye contact are successfully updated" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.upsertPAYEContact(ArgumentMatchers.eq(regId), ArgumentMatchers.any[PAYEContact]()))
        .thenReturn(Future.successful(validPAYEContact))

      val actual = await(service.upsertPAYEContact(regId, validPAYEContact))
      actual shouldBe validPAYEContact
    }
  }

  "Calling getCompletionCapacity" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveCompletionCapacity(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(None))

      val actual = await(service.getCompletionCapacity(regId))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveCompletionCapacity(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getCompletionCapacity(regId)) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveCompletionCapacity(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(Some("Director")))

      val actual = await(service.getCompletionCapacity(regId))
      actual shouldBe validRegistration.completionCapacity
    }
  }

  "Calling upsertCompletionCapacity" should {

    "return a DBNotFound response when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertCompletionCapacity(ArgumentMatchers.eq(regId), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      intercept[MissingRegDocument] { await(service.upsertCompletionCapacity(regId, "Agent")) }
    }

    "return a DBSuccess response when the paye contact are successfully updated" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.upsertCompletionCapacity(ArgumentMatchers.eq(regId), ArgumentMatchers.any()))
        .thenReturn(Future.successful("Agent"))

      val actual = await(service.upsertCompletionCapacity(regId, "Agent"))
      actual shouldBe "Agent"
    }
  }

  "Calling getAcknowledgementReference" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveAcknowledgementReference(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(None))

      val actual = await(service.getAcknowledgementReference(regId))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveAcknowledgementReference(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getAcknowledgementReference(regId)) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveAcknowledgementReference(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(Some("tstBRPY")))

      val actual = await(service.getAcknowledgementReference(regId))
      actual shouldBe Some("tstBRPY")
    }
  }

}
