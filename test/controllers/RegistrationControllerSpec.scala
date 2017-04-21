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

import java.time.LocalDate

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import common.exceptions.DBExceptions.MissingRegDocument
import fixtures.{AuthFixture, RegistrationFixture}
import helpers.PAYERegSpec
import models.incorporation.{IncorpStatusUpdate, IncorpStatusUpdate$}
import models._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import repositories.RegistrationMongoRepository
import services._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class RegistrationControllerSpec extends PAYERegSpec with AuthFixture with RegistrationFixture {

  val mockRegistrationService = mock[RegistrationService]
  val mockSubmissionService = mock[SubmissionService]
  val mockRepo = mock[RegistrationMongoRepository]
  val mockNotificationService = mock[NotificationService]

  implicit val system = ActorSystem("PR")
  implicit val materializer = ActorMaterializer()


  class Setup {
    val controller = new RegistrationCtrl {
      override val auth = mockAuthConnector
      override val resourceConn = mockRepo
      override val registrationService = mockRegistrationService
      override val submissionService = mockSubmissionService
      override val notificationService = mockNotificationService
    }
  }

  override def beforeEach() {
    reset(mockAuthConnector)
  }

  "Calling newPAYERegistration" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      AuthorisationMocks.mockNotLoggedInOrAuthorised

      val response = controller.newPAYERegistration("AC123456")(FakeRequest().withBody(Json.toJson[String]("NNASD9789F")))

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a PAYERegistration for a successful creation" in new Setup {
      AuthorisationMocks.mockSuccessfulAuthorisation("AC123456", validAuthority)
      when(mockRegistrationService.createNewPAYERegistration(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.contains("NNASD9789F"), ArgumentMatchers.eq(validAuthority.ids.internalId)))
        .thenReturn(Future.successful(validRegistration))

      val response = controller.newPAYERegistration("AC123456")(FakeRequest().withBody(Json.toJson[String]("NNASD9789F")))

      status(response) shouldBe Status.OK
    }
  }

  "Calling getPAYERegistration" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.getPAYERegistration("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is not authorised" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> "notAuthorised")))

      val response = controller.getPAYERegistration("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no authored resource" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.getPAYERegistration("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.fetchPAYERegistration(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(None))

      val response = controller.getPAYERegistration("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.fetchPAYERegistration(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(Some(validRegistration)))

      val response = controller.getPAYERegistration("AC123456")(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling getCompanyDetails" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.getCompanyDetails("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is not authorised" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> "notAuthorised")))

      val response = controller.getCompanyDetails("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no authored resource" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.getCompanyDetails("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.getCompanyDetails(ArgumentMatchers.contains("AC123456"))).thenReturn(Future.successful(None))

      val response = controller.getCompanyDetails("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.getCompanyDetails(ArgumentMatchers.contains("AC123456"))).thenReturn(Future.successful(validRegistration.companyDetails))

      val response = controller.getCompanyDetails("AC123456")(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling upsertCompanyDetails" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.upsertCompanyDetails("AC123456")(FakeRequest().withBody(Json.toJson[CompanyDetails](validCompanyDetails)))

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is not authorised" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> "notAuthorised")))

      val response = controller.upsertCompanyDetails("AC123456")(FakeRequest().withBody(Json.toJson[CompanyDetails](validCompanyDetails)))

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no authored resource" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.upsertCompanyDetails("AC123456")(FakeRequest().withBody(Json.toJson[CompanyDetails](validCompanyDetails)))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.upsertCompanyDetails(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[CompanyDetails]()))
        .thenReturn(Future.failed(new MissingRegDocument("AC123456")))

      val response = controller.upsertCompanyDetails("AC123456")(FakeRequest().withBody(Json.toJson[CompanyDetails](validCompanyDetails)))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return an OK response for a valid upsert" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.upsertCompanyDetails(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[CompanyDetails]()))
        .thenReturn(Future.successful(validCompanyDetails))

      val response = controller.upsertCompanyDetails("AC123456")(FakeRequest().withBody(Json.toJson[CompanyDetails](validCompanyDetails)))

      status(response) shouldBe Status.OK
    }
  }

  "Calling getEmployment" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.getEmployment("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is not authorised" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> "notAuthorised")))

      val response = controller.getEmployment("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no authored resource" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.getEmployment("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.getEmployment(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(None))

      val response = controller.getEmployment("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.getEmployment(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(validRegistration.employment))

      val response = controller.getEmployment("AC123456")(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling upsertEmployment" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.upsertEmployment("AC123456")(FakeRequest().withBody(Json.toJson[Employment](validEmployment)))

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is not authorised" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> "notAuthorised")))

      val response = controller.upsertEmployment("AC123456")(FakeRequest().withBody(Json.toJson[Employment](validEmployment)))

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no authored resource" in new Setup {

      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.upsertEmployment("AC123456")(FakeRequest().withBody(Json.toJson[Employment](validEmployment)))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {

      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.upsertEmployment(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[Employment]()))
        .thenReturn(Future.failed(new MissingRegDocument("AC123456")))

      val response = controller.upsertEmployment("AC123456")(FakeRequest().withBody(Json.toJson[Employment](validEmployment)))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return an OK response for a valid upsert" in new Setup {

      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.upsertEmployment(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[Employment]()))
        .thenReturn(Future.successful(validEmployment))

      val response = controller.upsertEmployment("AC123456")(FakeRequest().withBody(Json.toJson[Employment](validEmployment)))

      status(response) shouldBe Status.OK
    }
  }

  "Calling getDirectors" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.getDirectors("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is not authorised" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> "notAuthorised")))

      val response = controller.getDirectors("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no authored resource" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.getDirectors("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.getDirectors(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(Seq.empty))

      val response = controller.getDirectors("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.getDirectors(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(validRegistration.directors))

      val response = controller.getDirectors("AC123456")(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling upsertDirectors" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.upsertDirectors("AC123456")(FakeRequest().withBody(Json.toJson[Seq[Director]](validDirectors)))

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is not authorised" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> "notAuthorised")))

      val response = controller.upsertDirectors("AC123456")(FakeRequest().withBody(Json.toJson[Seq[Director]](validDirectors)))

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no authored resource" in new Setup {

      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.upsertDirectors("AC123456")(FakeRequest().withBody(Json.toJson[Seq[Director]](validDirectors)))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {

      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.upsertDirectors(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[Seq[Director]]()))
        .thenReturn(Future.failed(new MissingRegDocument("AC123456")))

      val response = controller.upsertDirectors("AC123456")(FakeRequest().withBody(Json.toJson[Seq[Director]](validDirectors)))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return an OK response for a valid upsert" in new Setup {

      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.upsertDirectors(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[Seq[Director]]()))
        .thenReturn(Future.successful(validDirectors))

      val response = controller.upsertDirectors("AC123456")(FakeRequest().withBody(Json.toJson[Seq[Director]](validDirectors)))

      status(response) shouldBe Status.OK
    }
  }

  "Calling getSICCodes" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.getSICCodes("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is not authorised" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> "notAuthorised")))

      val response = controller.getSICCodes("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no authored resource" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.getSICCodes("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.getSICCodes(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(Seq.empty))

      val response = controller.getSICCodes("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.getSICCodes(ArgumentMatchers.contains("AC123456")))
        .thenReturn(Future.successful(validRegistration.sicCodes))

      val response = controller.getSICCodes("AC123456")(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling upsertSICCodes" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.upsertSICCodes("AC123456")(FakeRequest().withBody(Json.toJson[Seq[SICCode]](validSICCodes)))

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is not authorised" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> "notAuthorised")))

      val response = controller.upsertSICCodes("AC123456")(FakeRequest().withBody(Json.toJson[Seq[SICCode]](validSICCodes)))

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no authored resource" in new Setup {

      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.upsertSICCodes("AC123456")(FakeRequest().withBody(Json.toJson[Seq[SICCode]](validSICCodes)))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {

      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.upsertSICCodes(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[Seq[SICCode]]()))
        .thenReturn(Future.failed(new MissingRegDocument("AC123456")))

      val response = controller.upsertSICCodes("AC123456")(FakeRequest().withBody(Json.toJson[Seq[SICCode]](validSICCodes)))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return an OK response for a valid upsert" in new Setup {

      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.upsertSICCodes(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[Seq[SICCode]]()))
        .thenReturn(Future.successful(validSICCodes))

      val response = controller.upsertSICCodes("AC123456")(FakeRequest().withBody(Json.toJson[Seq[SICCode]](validSICCodes)))

      status(response) shouldBe Status.OK
    }
  }

  "Calling getPAYEContact" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.getPAYEContact("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is not authorised" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> "notAuthorised")))

      val response = controller.getPAYEContact("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no authored resource" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.getPAYEContact("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.getPAYEContact(ArgumentMatchers.contains("AC123456"))).thenReturn(Future.successful(None))

      val response = controller.getPAYEContact("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.getPAYEContact(ArgumentMatchers.contains("AC123456"))).thenReturn(Future.successful(validRegistration.payeContact))

      val response = controller.getPAYEContact("AC123456")(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling upsertPAYEContact" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.upsertPAYEContact("AC123456")(FakeRequest().withBody(Json.toJson[PAYEContact](validPAYEContact)))

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is not authorised" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> "notAuthorised")))

      val response = controller.upsertPAYEContact("AC123456")(FakeRequest().withBody(Json.toJson[PAYEContact](validPAYEContact)))

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no authored resource" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.upsertPAYEContact("AC123456")(FakeRequest().withBody(Json.toJson[PAYEContact](validPAYEContact)))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.upsertPAYEContact(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[PAYEContact]()))
        .thenReturn(Future.failed(new MissingRegDocument("AC123456")))

      val response = controller.upsertPAYEContact("AC123456")(FakeRequest().withBody(Json.toJson[PAYEContact](validPAYEContact)))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return an OK response for a valid upsert" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.upsertPAYEContact(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any[PAYEContact]()))
        .thenReturn(Future.successful(validPAYEContact))

      val response = controller.upsertPAYEContact("AC123456")(FakeRequest().withBody(Json.toJson[PAYEContact](validPAYEContact)))

      status(response) shouldBe Status.OK
    }
  }

  "Calling getCompletionCapacity" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.getCompletionCapacity("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is not authorised" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> "notAuthorised")))

      val response = controller.getCompletionCapacity("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no authored resource" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.getCompletionCapacity("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.getCompletionCapacity(ArgumentMatchers.contains("AC123456"))).thenReturn(Future.successful(None))

      val response = controller.getCompletionCapacity("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.getCompletionCapacity(ArgumentMatchers.contains("AC123456"))).thenReturn(Future.successful(validRegistration.completionCapacity))

      val response = controller.getCompletionCapacity("AC123456")(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling upsertCompletionCapacity" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.upsertCompletionCapacity("AC123456")(FakeRequest().withBody(Json.toJson[String]("Director")))

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is not authorised" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> "notAuthorised")))

      val response = controller.upsertCompletionCapacity("AC123456")(FakeRequest().withBody(Json.toJson[String]("Director")))

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no authored resource" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.upsertCompletionCapacity("AC123456")(FakeRequest().withBody(Json.toJson[String]("Director")))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.upsertCompletionCapacity(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new MissingRegDocument("AC123456")))

      val response = controller.upsertCompletionCapacity("AC123456")(FakeRequest().withBody(Json.toJson[String]("Director")))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return an OK response for a valid upsert" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.upsertCompletionCapacity(ArgumentMatchers.contains("AC123456"), ArgumentMatchers.any()))
        .thenReturn(Future.successful("Director"))

      val response = controller.upsertCompletionCapacity("AC123456")(FakeRequest().withBody(Json.toJson[String]("Director")))

      status(response) shouldBe Status.OK
    }
  }

  "Calling submitPAYERegistration" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.submitPAYERegistration("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is not authorised" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> "notAuthorised")))

      val response = controller.submitPAYERegistration("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no authored resource" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.submitPAYERegistration("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return an Ok response with acknowledgement reference for a valid submit" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockSubmissionService.submitToDes(ArgumentMatchers.contains("AC123456"))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.successful("BRPY00000000001"))

      val response = controller.submitPAYERegistration("AC123456")(FakeRequest())

      status(response) shouldBe Status.OK
      jsonBodyOf(await(response)) shouldBe Json.toJson("BRPY00000000001")
    }
  }

  "Calling getAcknowledgementReference" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.getAcknowledgementReference("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is not authorised" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> "notAuthorised")))

      val response = controller.getAcknowledgementReference("AC123456")(FakeRequest())

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Not Found response if there is no authored resource" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val response = controller.getAcknowledgementReference("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.getAcknowledgementReference(ArgumentMatchers.contains("AC123456"))).thenReturn(Future.successful(None))

      val response = controller.getAcknowledgementReference("AC123456")(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

      when(mockRegistrationService.getAcknowledgementReference(ArgumentMatchers.contains("AC123456"))).thenReturn(Future.successful(Some("TESTBRPY001")))

      val response = controller.getAcknowledgementReference("AC123456")(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "getEligibility" should {
    "return an OK" when {
      "the users eligibility has been found" in new Setup {
        when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some(validAuthority)))

        when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

        when(mockRegistrationService.getEligibility("AC123456"))
          .thenReturn(Future.successful(Some(Eligibility(false, false))))

        val result = controller.getEligibility("AC123456")(FakeRequest())
        status(result) shouldBe Status.OK
      }
    }

    "return a NOT FOUND" when {
      "the eligibility cannot be found" in new Setup {
        when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some(validAuthority)))

        when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

        when(mockRegistrationService.getEligibility("AC123456"))
          .thenReturn(Future.successful(None))

        val result = controller.getEligibility("AC123456")(FakeRequest())
        status(result) shouldBe Status.NOT_FOUND
      }

      "the auth resource cannot be found" in new Setup {
        when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some(validAuthority)))

        when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(None))

        val result = controller.getEligibility("AC123456")(FakeRequest())
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "return FORBIDDEN" when {
      "NotLoggedInOrAuthorised" in new Setup {
        when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(None))

        when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(None))

        val result = controller.getEligibility("AC123456")(FakeRequest())
        status(result) shouldBe Status.FORBIDDEN
      }

      "NotAuthorised on the requested resource" in new Setup {
        when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some(validAuthority)))

        when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some("AC123456" -> "invalid")))

        val result = controller.getEligibility("AC123456")(FakeRequest())
        status(result) shouldBe Status.FORBIDDEN
      }
    }
  }

  "updateEligibility" should {
    "return an OK" when {
      "the eligibility has been updated" in new Setup {
        when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some(validAuthority)))

        when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

        when(mockRegistrationService.updateEligibility(ArgumentMatchers.eq("AC123456"), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Eligibility(false, false)))

        val result = controller.updateEligibility("AC123456")(FakeRequest().withBody(Json.toJson(Eligibility(false, false))))
        status(result) shouldBe Status.OK
      }
    }

    "return a NOT FOUND" when {
      "the auth resource cannot be found" in new Setup {
        when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some(validAuthority)))

        when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(None))

        val result = controller.updateEligibility("AC123456")(FakeRequest().withBody(Json.toJson(Eligibility(false, false))))
        status(result) shouldBe Status.NOT_FOUND
      }

      "the reg document cannot found against the reg id" in new Setup {
        when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some(validAuthority)))

        when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some("AC123456" -> validAuthority.ids.internalId)))

        when(mockRegistrationService.updateEligibility(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.failed(new MissingRegDocument("AC123456")))

        val result = controller.updateEligibility("AC123456")(FakeRequest().withBody(Json.toJson(Eligibility(false, false))))
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "return FORBIDDEN" when {
      "NotLoggedInOrAuthorised" in new Setup {
        when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(None))

        when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(None))

        val result = controller.updateEligibility("AC123456")(FakeRequest().withBody(Json.toJson(Eligibility(false, false))))
        status(result) shouldBe Status.FORBIDDEN
      }

      "NotAuthorised on the requested resource" in new Setup {
        when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some(validAuthority)))

        when(mockRepo.getInternalId(ArgumentMatchers.eq("AC123456"))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some("AC123456" -> "invalid")))

        val result = controller.updateEligibility("AC123456")(FakeRequest().withBody(Json.toJson(Eligibility(false, false))))
        status(result) shouldBe Status.FORBIDDEN
      }
    }
  }

  "updateRegistrationWithEmpRef" should {
    "return an OK" when {
      "the reg doc has been updated with the emp ref" in new Setup {
        val testNotification = EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "04")
        val request = FakeRequest().withBody(Json.toJson(testNotification))

        when(mockNotificationService.processNotification(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(testNotification))

        val result = controller.updateRegistrationWithEmpRef("testAckRef")(request)
        status(result) shouldBe Status.OK
      }
    }
  }
}
