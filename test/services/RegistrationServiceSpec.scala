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
import fixtures.RegistrationFixture
import models.{CompanyDetails, Director, Employment, PAYEContact, SICCode}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import testHelpers.PAYERegSpec

import scala.concurrent.Future

class RegistrationServiceSpec extends PAYERegSpec with RegistrationFixture {

  class Setup {
    val service = new RegistrationSrv {
      override val registrationRepository = mockRegistrationRepository
    }
  }

  "Calling newPAYERegistration" should {

    "return a DBDuplicate response when the database already has a PAYERegistration" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.contains("AC123456"))).thenReturn(Future.successful(Some(validRegistration)))

      val actual = await(service.createNewPAYERegistration("AC123456", validRegistration.internalID))
      actual shouldBe validRegistration
    }

    "return a DBSuccess response when the Registration is correctly inserted into the database" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.contains("AC123456"))).thenReturn(Future.successful(None))
      when(mockRegistrationRepository.createNewRegistration(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[String]())).thenReturn(Future.successful(validRegistration))

      val actual = await(service.createNewPAYERegistration("AC123456", "09876"))
      actual shouldBe validRegistration
    }
  }

  "Calling fetchPAYERegistration" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.contains("AC123456"))).thenReturn(Future.successful(None))

      val actual = await(service.fetchPAYERegistration("AC123456"))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.contains("AC123456"))).thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.fetchPAYERegistration("AC123456")) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.contains("AC123456"))).thenReturn(Future.successful(Some(validRegistration)))

      val actual = await(service.fetchPAYERegistration("AC123456"))
      actual shouldBe Some(validRegistration)
    }
  }

  "Calling getCompanyDetails" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveCompanyDetails(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(None))

      val actual = await(service.getCompanyDetails("AC123456"))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveCompanyDetails(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getCompanyDetails("AC123456")) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveCompanyDetails(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(Some(validCompanyDetails)))

      val actual = await(service.getCompanyDetails("AC123456"))
      actual shouldBe validRegistration.companyDetails
    }
  }

  "Calling upsertCompanyDetails" should {

    "return a DBNotFound response when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertCompanyDetails(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[CompanyDetails]()))
        .thenReturn(Future.failed(new MissingRegDocument("AC123456")))

      intercept[MissingRegDocument] { await(service.upsertCompanyDetails("AC123456", validCompanyDetails)) }
    }

    "return a DBSuccess response when the company details are successfully updated" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.upsertCompanyDetails(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[CompanyDetails]()))
        .thenReturn(Future.successful(validCompanyDetails))

      val actual = await(service.upsertCompanyDetails("AC123456", validCompanyDetails))
      actual shouldBe validCompanyDetails
    }
  }

  "Calling getEmployment" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveEmployment(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(None))

      val actual = await(service.getEmployment("AC123456"))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveEmployment(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getEmployment("AC123456")) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveEmployment(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(Some(validEmployment)))

      val actual = await(service.getEmployment("AC123456"))
      actual shouldBe validRegistration.employment
    }
  }

  "Calling upsertEmployment" should {

    "return a DBNotFound response when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertEmployment(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[Employment]()))
        .thenReturn(Future.failed(new MissingRegDocument("AC123456")))

      intercept[MissingRegDocument] { await(service.upsertEmployment("AC123456", validEmployment)) }
    }

    "return a DBSuccess response when the company details are successfully updated" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.upsertEmployment(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[Employment]()))
        .thenReturn(Future.successful(validEmployment))

      val actual = await(service.upsertEmployment("AC123456", validEmployment))
      actual shouldBe validEmployment
    }
  }

  "Calling getDirectors" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveDirectors(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(Seq.empty))

      val actual = await(service.getDirectors("AC123456"))
      actual shouldBe Seq.empty
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveDirectors(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getDirectors("AC123456")) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveDirectors(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(validDirectors))

      val actual = await(service.getDirectors("AC123456"))
      actual shouldBe validRegistration.directors
    }
  }

  "Calling upsertDirectors" should {

    "return a DBNotFound response when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertDirectors(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[Seq[Director]]()))
        .thenReturn(Future.failed(new MissingRegDocument("AC123456")))

      intercept[MissingRegDocument] { await(service.upsertDirectors("AC123456", validDirectors)) }
    }

    "return a DBSuccess response when the company details are successfully updated" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.upsertDirectors(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[Seq[Director]]()))
        .thenReturn(Future.successful(validDirectors))

      val actual = await(service.upsertDirectors("AC123456", validDirectors))
      actual shouldBe validDirectors
    }
  }

  "Calling getSICCodes" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveSICCodes(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(Seq.empty))

      val actual = await(service.getSICCodes("AC123456"))
      actual shouldBe Seq.empty
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveSICCodes(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getSICCodes("AC123456")) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveSICCodes(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(validSICCodes))

      val actual = await(service.getSICCodes("AC123456"))
      actual shouldBe validRegistration.sicCodes
    }
  }

  "Calling upsertSICCodes" should {

    "return a DBNotFound response when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertSICCodes(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[Seq[SICCode]]()))
        .thenReturn(Future.failed(new MissingRegDocument("AC123456")))

      intercept[MissingRegDocument] { await(service.upsertSICCodes("AC123456", validSICCodes)) }
    }

    "return a DBSuccess response when the company details are successfully updated" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.upsertSICCodes(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[Seq[SICCode]]()))
        .thenReturn(Future.successful(validSICCodes))

      val actual = await(service.upsertSICCodes("AC123456", validSICCodes))
      actual shouldBe validSICCodes
    }
  }

  "Calling getPAYEContact" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrievePAYEContact(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(None))

      val actual = await(service.getPAYEContact("AC123456"))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrievePAYEContact(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getPAYEContact("AC123456")) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrievePAYEContact(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(Some(validPAYEContact)))

      val actual = await(service.getPAYEContact("AC123456"))
      actual shouldBe validRegistration.payeContactDetails
    }
  }

  "Calling upsertPAYEContact" should {

    "return a DBNotFound response when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertPAYEContact(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[PAYEContact]()))
        .thenReturn(Future.failed(new MissingRegDocument("AC123456")))

      intercept[MissingRegDocument] { await(service.upsertPAYEContact("AC123456", validPAYEContact)) }
    }

    "return a DBSuccess response when the paye contact are successfully updated" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.upsertPAYEContact(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[PAYEContact]()))
        .thenReturn(Future.successful(validPAYEContact))

      val actual = await(service.upsertPAYEContact("AC123456", validPAYEContact))
      actual shouldBe validPAYEContact
    }
  }

}
