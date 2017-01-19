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

package controllers

import common.exceptions.DBExceptions.MissingRegDocument
import fixtures.{AuthFixture, RegistrationFixture}
import models.CompanyDetails
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import services._
import testHelpers.PAYERegSpec

import scala.concurrent.Future

class RegistrationControllerSpec extends PAYERegSpec with AuthFixture with RegistrationFixture {

  val mockRegistrationService = mock[RegistrationService]

  class Setup {
    val controller = new RegistrationController {
      override val auth = mockAuthConnector
      override val registrationService = mockRegistrationService
    }
  }

  "Calling newPAYERegistration" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      AuthorisationMocks.mockNotLoggedInOrAuthorised

      val response = controller.newPAYERegistration("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a PAYERegistration for a successful creation" in new Setup {
      AuthorisationMocks.mockSuccessfulAuthorisation("AC123456", validAuthority)
      when(mockRegistrationService.createNewPAYERegistration(Matchers.contains("AC123456"))).thenReturn(Future.successful(validRegistration))

      val response = controller.newPAYERegistration("AC123456")(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling getPAYERegistration" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      AuthorisationMocks.mockNotLoggedInOrAuthorised

      val response = controller.getPAYERegistration("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockSuccessfulAuthorisation("AC123456", validAuthority)
      when(mockRegistrationService.fetchPAYERegistration(Matchers.contains("AC123456"))).thenReturn(Future.successful(None))

      val response = controller.getPAYERegistration("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockSuccessfulAuthorisation("AC123456", validAuthority)
      when(mockRegistrationService.fetchPAYERegistration(Matchers.contains("AC123456"))).thenReturn(Future.successful(Some(validRegistration)))

      val response = controller.getPAYERegistration("AC123456")(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling getCompanyDetails" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      AuthorisationMocks.mockNotLoggedInOrAuthorised

      val response = controller.getCompanyDetails("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockSuccessfulAuthorisation("AC123456", validAuthority)
      when(mockRegistrationService.getCompanyDetails(Matchers.contains("AC123456"))).thenReturn(Future.successful(None))

      val response = controller.getCompanyDetails("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockSuccessfulAuthorisation("AC123456", validAuthority)
      when(mockRegistrationService.getCompanyDetails(Matchers.contains("AC123456"))).thenReturn(Future.successful(validRegistration.companyDetails))

      val response = controller.getCompanyDetails("AC123456")(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling upsertCompanyDetails" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      AuthorisationMocks.mockNotLoggedInOrAuthorised

      val response = controller.upsertCompanyDetails("AC123456")(FakeRequest().withBody(Json.toJson[CompanyDetails](validCompanyDetails)))

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockSuccessfulAuthorisation("AC123456", validAuthority)
      when(mockRegistrationService.upsertCompanyDetails(Matchers.contains("AC123456"), Matchers.any())).thenReturn(Future.failed(new MissingRegDocument("AC123456")))

      val response = controller.upsertCompanyDetails("AC123456")(FakeRequest().withBody(Json.toJson[CompanyDetails](validCompanyDetails)))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return an OK response for a valid upsert" in new Setup {
      AuthorisationMocks.mockSuccessfulAuthorisation("AC123456", validAuthority)
      when(mockRegistrationService.upsertCompanyDetails(Matchers.contains("AC123456"), Matchers.any())).thenReturn(Future.successful(validCompanyDetails))

      val response = controller.upsertCompanyDetails("AC123456")(FakeRequest().withBody(Json.toJson[CompanyDetails](validCompanyDetails)))

      status(response) shouldBe Status.OK
    }
  }

}
