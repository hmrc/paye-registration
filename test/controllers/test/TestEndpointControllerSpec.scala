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

package controllers.test

import fixtures.{RegistrationFixture, AuthFixture}
import models.PAYERegistration
import play.api.libs.json.Json
import repositories.RegistrationMongoRepository
import play.api.test.FakeRequest
import play.api.http.Status
import org.mockito.Matchers
import org.mockito.Mockito._
import testHelpers.PAYERegSpec

import scala.concurrent.Future

class TestEndpointControllerSpec extends PAYERegSpec with AuthFixture with RegistrationFixture {

  val mockRepo = mock[RegistrationMongoRepository]

  class Setup {
    val controller = new TestEndpointController {
      override val auth = mockAuthConnector
      override val registrationRepository = mockRepo
    }
  }

  "Teardown registration collection" should {
    "return a 200 response for success" in new Setup {
      when(mockRepo.dropCollection).thenReturn(Future.successful(()))

      val response = await(controller.registrationTeardown()(FakeRequest()))
      status(response) shouldBe Status.OK
    }

    "return a 500 response for failure" in new Setup {
      when(mockRepo.dropCollection).thenReturn(Future.failed(new RuntimeException("test failure message")))

      val response = await(controller.registrationTeardown()(FakeRequest()))
      status(response) shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }

  "Delete Registration" should {
    "return a 200 response for success" in new Setup {
      when(mockRepo.deleteRegistration(Matchers.any())).thenReturn(Future.successful(true))

      val response = await(controller.deleteRegistration("AC123456")(FakeRequest()))
      status(response) shouldBe Status.OK
    }

    "return a 500 response for failure" in new Setup {
      when(mockRepo.deleteRegistration(Matchers.any())).thenReturn(Future.failed(new RuntimeException("test failure message")))

      val response = await(controller.deleteRegistration("AC123456")(FakeRequest()))
      status(response) shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }

  "Insert Registration" should {
    "return a 200 response for success" in new Setup {
      when(mockRepo.addRegistration(Matchers.any())).thenReturn(Future.successful(validRegistration))

      val response = await(controller.insertRegistration("AC123456")(FakeRequest().withBody(Json.toJson[PAYERegistration](validRegistration))))
      status(response) shouldBe Status.OK
    }

    "return a 500 response for failure" in new Setup {
      when(mockRepo.addRegistration(Matchers.any())).thenReturn(Future.failed(new RuntimeException("test failure message")))

      val response = await(controller.insertRegistration("AC123456")(FakeRequest().withBody(Json.toJson[PAYERegistration](validRegistration))))
      status(response) shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }

}