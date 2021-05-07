/*
 * Copyright 2021 HM Revenue & Customs
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

import auth.CryptoSCRS
import enums.PAYEStatus
import fixtures.RegistrationFixture
import helpers.PAYERegSpec
import models.PAYERegistration
import models.validation.APIValidation
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.RegistrationMongoRepository

import scala.concurrent.Future

class TestEndpointControllerSpec extends PAYERegSpec with RegistrationFixture {

  val mockRepo = mock[RegistrationMongoRepository]

  class Setup {
    val controller = new TestEndpointController(mockRepo, mockAuthConnector, mockCrypto, stubControllerComponents())
  }

  override def beforeEach() {
    reset(mockRepo)
    reset(mockAuthConnector)
  }

  "Teardown registration collection" should {
    "return a 200 response for success" in new Setup {
      when(mockRepo.dropCollection(ArgumentMatchers.any()))
        .thenReturn(Future.successful(()))

      val response = controller.registrationTeardown()(FakeRequest())
      status(response) shouldBe Status.OK
    }

    "return a 500 response for failure" in new Setup {
      when(mockRepo.dropCollection(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new RuntimeException("test failure message")))

      val response = controller.registrationTeardown()(FakeRequest())
      status(response) shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }

  "Delete Registration" should {
    "return a 200 response for success" in new Setup {
      when(mockRepo.deleteRegistration(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      val response = controller.deleteRegistration("AC123456")(FakeRequest())
      status(response) shouldBe Status.OK
    }

    "return a 500 response for failure" in new Setup {
      when(mockRepo.deleteRegistration(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new RuntimeException("test failure message")))

      val response = controller.deleteRegistration("AC123456")(FakeRequest())
      status(response) shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }

  "Insert Registration" should {
    val testInternalId = "testInternalID"

    "return a 200 response for success" in new Setup {
      AuthorisationMocks.mockAuthenticated(testInternalId)
      implicit val f = PAYERegistration.format(APIValidation, new CryptoSCRS(Configuration("json.encryption.key" -> "MTIzNDU2Nzg5MDEyMzQ1Ng==")))
      when(mockRepo.updateRegistration(ArgumentMatchers.any()))
        .thenReturn(Future.successful(validRegistration))

      val response = controller.updateRegistration("AC123456")(FakeRequest().withBody(Json.toJson[PAYERegistration](validRegistration)))
      status(response) shouldBe Status.OK
    }

    "return a 500 response for failure" in new Setup {
      AuthorisationMocks.mockAuthenticated(testInternalId)
      implicit val f = PAYERegistration.format(APIValidation, new CryptoSCRS(Configuration("json.encryption.key" -> "MTIzNDU2Nzg5MDEyMzQ1Ng==")))
      when(mockRepo.updateRegistration(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new RuntimeException("test failure message")))

      val response = controller.updateRegistration("AC123456")(FakeRequest().withBody(Json.toJson[PAYERegistration](validRegistration)))
      status(response) shouldBe Status.INTERNAL_SERVER_ERROR
    }

    "return a Bad Request response for incorrect Json" in new Setup {
      AuthorisationMocks.mockAuthenticated(testInternalId)

      val response = controller.updateRegistration("AC123456")(FakeRequest().withBody(Json.parse("""{"formCreationTimestamp":"testTimestamp","regID":"invalid"}""")))
      status(response) shouldBe Status.BAD_REQUEST
    }

    "return a forbidden response for unauthorised" in new Setup {
      AuthorisationMocks.mockAuthenticated(testInternalId)
      implicit val f = PAYERegistration.format(APIValidation, new CryptoSCRS(Configuration("json.encryption.key" -> "MTIzNDU2Nzg5MDEyMzQ1Ng==")))
      val response = controller.updateRegistration("AC123456")(FakeRequest().withBody(Json.toJson[PAYERegistration](validRegistration)))
      status(response) shouldBe Status.FORBIDDEN
    }
  }

  "newStatus" should {
    "return a 200 response for success" in new Setup {
      when(mockRepo.createNewRegistration(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(validRegistration))

      when(mockRepo.updateRegistrationStatus(ArgumentMatchers.eq("AC123456"), ArgumentMatchers.any()))
          .thenReturn(Future.successful(PAYEStatus.draft))

      when(mockRepo.updateRegistrationStatus(ArgumentMatchers.eq("AC654321"), ArgumentMatchers.any()))
          .thenReturn(Future.failed(new RuntimeException("")))

      def newstatus(s: String) = controller.newStatus("AC123456", s)(FakeRequest())
      status(newstatus("draft")) shouldBe Status.OK
      status(newstatus("held")) shouldBe Status.OK
      status(newstatus("submitted")) shouldBe Status.OK
      status(newstatus("acknowledged")) shouldBe Status.OK
      status(newstatus("invalid")) shouldBe Status.OK
      status(newstatus("cancelled")) shouldBe Status.OK
      status(newstatus("rejected")) shouldBe Status.OK
      status(controller.newStatus("AC654321", "bananaFruitcake")(FakeRequest())) shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }

  "updateStatus" should {
    "return a 200 response for success" in new Setup {

      when(mockRepo.updateRegistrationStatus(ArgumentMatchers.eq("AC123456"), ArgumentMatchers.any()))
        .thenReturn(Future.successful(PAYEStatus.draft))

      status(controller.updateStatus("AC123456", "submitted")(FakeRequest())) shouldBe Status.OK
    }
  }

}
