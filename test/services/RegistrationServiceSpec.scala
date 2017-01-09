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

import common.exceptions.DBExceptions.{UpdateFailed, MissingRegDocument}
import fixtures.RegistrationFixture
import helpers.PAYERegSpec
import repositories.RegistrationMongoRepository
import org.mockito.Matchers
import org.mockito.Mockito._

import scala.concurrent.Future

class RegistrationServiceSpec extends PAYERegSpec with RegistrationFixture {

  val mockRegistrationRepository = mock[RegistrationMongoRepository]

  class Setup {
    val service = new RegistrationService {
      override val registrationRepository = mockRegistrationRepository
    }
  }

  "Calling fetchPAYERegistration" should {

    "return a DBNotFound response when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(Matchers.contains("AC123456"))).thenReturn(Future.successful(None))

      val actual = await(service.fetchPAYERegistration("AC123456"))
      actual shouldBe DBNotFoundResponse
    }

    "return a DBError response when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveRegistration(Matchers.contains("AC123456"))).thenReturn(Future.failed(exception))

      val actual = await(service.fetchPAYERegistration("AC123456"))
      actual shouldBe DBErrorResponse(exception)
    }

    "return a DBSuccess response when there is a registration matching the user's ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(Matchers.contains("AC123456"))).thenReturn(Future.successful(Some(validRegistration)))

      val actual = await(service.fetchPAYERegistration("AC123456"))
      actual shouldBe DBSuccessResponse(validRegistration)
    }
  }

  "Calling upsertCompanyDetails" should {

    "return a DBNotFound response when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertCompanyDetails(Matchers.contains("AC123456"), Matchers.any())).thenReturn(Future.failed(new MissingRegDocument("AC123456")))

      val actual = await(service.upsertCompanyDetails("AC123456", validCompanyDetails))
      actual shouldBe DBNotFoundResponse
    }

    "return a DBError response when the database errors" in new Setup {
      val updateException = new UpdateFailed("AC123456", "CompanyDetails")
      when(mockRegistrationRepository.upsertCompanyDetails(Matchers.contains("AC123456"), Matchers.any())).thenReturn(Future.failed(updateException))

      val actual = await(service.upsertCompanyDetails("AC123456", validCompanyDetails))
      actual shouldBe DBErrorResponse(updateException)
    }

    "return a DBSuccess response when the company details are successfully updated" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.upsertCompanyDetails(Matchers.contains("AC123456"), Matchers.any())).thenReturn(Future.successful(validCompanyDetails))

      val actual = await(service.upsertCompanyDetails("AC123456", validCompanyDetails))
      actual shouldBe DBSuccessResponse(validCompanyDetails)
    }
  }

}
