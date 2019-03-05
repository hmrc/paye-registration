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

package controllers

import auth.AuthorisationResource
import common.exceptions.RegistrationExceptions.UnmatchedStatusException
import helpers.PAYERegSpec
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.http.Status
import play.api.test.FakeRequest
import services.RegistrationService

import scala.concurrent.Future

class RepositoryControllerSpec extends PAYERegSpec {

  val mockRegistrationService = mock[RegistrationService]

  class Setup {
    val controller = new RepositoryCtrl {
      override val registrationService: RegistrationService = mockRegistrationService
      override val resourceConn: AuthorisationResource = mockRegistrationRepository
      val authConnector = mockAuthConnector
    }
  }

  override def beforeEach(): Unit = {
    reset(mockAuthConnector)
    reset(mockRegistrationService)
    reset(mockRegistrationRepository)
  }

  val regId = "AC123456"
  val testInternalId = "testInternalID"

  "Calling deleteRegistrationFromDashboard" should {
    "return an InternalServerError response if there is a mongo problem" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.deletePAYERegistration(ArgumentMatchers.eq(regId), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(false))

      val response = controller.deleteRegistrationFromDashboard(regId)(FakeRequest())

      status(response) shouldBe Status.INTERNAL_SERVER_ERROR
    }
    "return an Ok response if an invalid or draft document has been deleted" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.deletePAYERegistration(ArgumentMatchers.eq(regId), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      val response = controller.deleteRegistrationFromDashboard(regId)(FakeRequest())

      status(response) shouldBe Status.OK
    }
    "return a PreconditionFailed response of the document is not invalid or draft status" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.deletePAYERegistration(ArgumentMatchers.eq(regId), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new UnmatchedStatusException))

      val response = controller.deleteRegistrationFromDashboard(regId)(FakeRequest())

      status(response) shouldBe Status.PRECONDITION_FAILED
    }
  }
}
