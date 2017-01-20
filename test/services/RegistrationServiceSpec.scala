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
import repositories.RegistrationMongoRepository
import org.mockito.Matchers
import org.mockito.Mockito._
import testHelpers.PAYERegSpec

import scala.concurrent.Future

class RegistrationServiceSpec extends PAYERegSpec with RegistrationFixture {

  val mockRegistrationRepository = mock[RegistrationMongoRepository]

  class Setup {
    val service = new RegistrationService {
      override val registrationRepository = mockRegistrationRepository
    }
  }

  "Calling newPAYERegistration" should {

    "return a DBDuplicate response when the database already has a PAYERegistration" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(Matchers.contains("AC123456"))).thenReturn(Future.successful(Some(validRegistration)))

      val actual = await(service.createNewPAYERegistration("AC123456"))
      actual shouldBe validRegistration
    }

    "return a DBSuccess response when the Registration is correctly inserted into the database" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(Matchers.contains("AC123456"))).thenReturn(Future.successful(None))
      when(mockRegistrationRepository.createNewRegistration(Matchers.contains("AC123456"))).thenReturn(Future.successful(validRegistration))

      val actual = await(service.createNewPAYERegistration("AC123456"))
      actual shouldBe validRegistration
    }
  }

  "Calling fetchPAYERegistration" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(Matchers.contains("AC123456"))).thenReturn(Future.successful(None))

      val actual = await(service.fetchPAYERegistration("AC123456"))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveRegistration(Matchers.contains("AC123456"))).thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.fetchPAYERegistration("AC123456")) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(Matchers.contains("AC123456"))).thenReturn(Future.successful(Some(validRegistration)))

      val actual = await(service.fetchPAYERegistration("AC123456"))
      actual shouldBe Some(validRegistration)
    }
  }

  "Calling getCompanyDetails" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveCompanyDetails(Matchers.contains("AC123456")))
        .thenReturn(Future.successful(None))

      val actual = await(service.getCompanyDetails("AC123456"))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveCompanyDetails(Matchers.contains("AC123456")))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getCompanyDetails("AC123456")) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveCompanyDetails(Matchers.contains("AC123456")))
        .thenReturn(Future.successful(Some(validCompanyDetails)))

      val actual = await(service.getCompanyDetails("AC123456"))
      actual shouldBe validRegistration.companyDetails
    }
  }

  "Calling upsertCompanyDetails" should {

    "return a DBNotFound response when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertCompanyDetails(Matchers.contains("AC123456"), Matchers.any())).thenReturn(Future.failed(new MissingRegDocument("AC123456")))

      intercept[MissingRegDocument] { await(service.upsertCompanyDetails("AC123456", validCompanyDetails)) }
    }

    "return a DBSuccess response when the company details are successfully updated" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.upsertCompanyDetails(Matchers.contains("AC123456"), Matchers.any())).thenReturn(Future.successful(validCompanyDetails))

      val actual = await(service.upsertCompanyDetails("AC123456", validCompanyDetails))
      actual shouldBe validCompanyDetails
    }
  }

  "Calling getEmployment" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveEmployment(Matchers.contains("AC123456")))
        .thenReturn(Future.successful(None))

      val actual = await(service.getEmployment("AC123456"))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveEmployment(Matchers.contains("AC123456")))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getEmployment("AC123456")) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveEmployment(Matchers.contains("AC123456")))
        .thenReturn(Future.successful(Some(validEmployment)))

      val actual = await(service.getEmployment("AC123456"))
      actual shouldBe validRegistration.employment
    }
  }

}
