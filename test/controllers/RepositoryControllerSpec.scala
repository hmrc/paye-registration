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

import auth.AuthorisationResource
import connectors.AuthConnect
import enums.PAYEStatus
import fixtures.AuthFixture
import helpers.PAYERegSpec
import org.mockito.ArgumentMatchers
import repositories.RegistrationMongoRepository
import services.RegistrationService
import org.mockito.Mockito._
import play.api.http.Status
import play.api.test.FakeRequest
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class RepositoryControllerSpec extends PAYERegSpec with AuthFixture {

  val mockRegistrationService = mock[RegistrationService]
  val mockRepo = mock[RegistrationMongoRepository]

  class Setup {
    val controller = new RepositoryCtrl {
      override val registraitonService: RegistrationService = mockRegistrationService
      override val resourceConn: AuthorisationResource[String] = mockRepo
      override val auth: AuthConnect = mockAuthConnector
    }
  }

  override def beforeEach(): Unit = {
    reset(mockAuthConnector)
  }

  def validAuth() = {
    when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
      .thenReturn(Future.successful(Some(validAuthority)))

    when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
      .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))
  }

  "Calling deleteRegistrationFromDashboard" should {
    "return a Forbidden response" when {
      "the user is not logged in" in new Setup {
        when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(None))

        when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(None))

        val response = controller.deleteRegistrationFromDashboard("AC123456")(FakeRequest())

        status(response) shouldBe Status.FORBIDDEN
      }

      "the user is not authorised" in new Setup {
        when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some(validAuthority)))

        when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some("AC123456" -> "notAuthorised")))

        val response = controller.deleteRegistrationFromDashboard("AC123456")(FakeRequest())

        status(response) shouldBe Status.FORBIDDEN
      }
    }
    "return a Not Found response" when {
      "there is no auth resource" in new Setup {
        when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some(validAuthority)))

        when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(None))

        val response = controller.deleteRegistrationFromDashboard("AC123456")(FakeRequest())

        status(response) shouldBe Status.NOT_FOUND
      }
    }
    "return an InternalServerError response" when {
      "there is a mongo problem" in new Setup {
        validAuth()

        when(mockRegistrationService.deletePAYERegistration(ArgumentMatchers.eq("AC123456"), ArgumentMatchers.any()))
          .thenReturn(Future.successful(false))

        val response = controller.deleteRegistrationFromDashboard("AC123456")(FakeRequest())

        status(response) shouldBe Status.INTERNAL_SERVER_ERROR
      }
    }
    "return an Ok response" when {
      "an invalid or draft document has been deleted" in new Setup {
        validAuth()

        when(mockRegistrationService.deletePAYERegistration(ArgumentMatchers.eq("AC123456"), ArgumentMatchers.any()))
          .thenReturn(Future.successful(true))

        val response = controller.deleteRegistrationFromDashboard("AC123456")(FakeRequest())

        status(response) shouldBe Status.OK
      }
    }
  }
}
