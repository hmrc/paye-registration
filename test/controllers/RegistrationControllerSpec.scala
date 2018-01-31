/*
 * Copyright 2018 HM Revenue & Customs
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
import common.exceptions.DBExceptions.{MissingRegDocument, RetrieveFailed, UpdateFailed}
import common.exceptions.RegistrationExceptions.{EmploymentDetailsNotDefinedException, RegistrationFormatException, UnmatchedStatusException}
import common.exceptions.SubmissionExceptions.{ErrorRegistrationException, RegistrationInvalidStatus}
import enums.PAYEStatus
import fixtures.RegistrationFixture
import helpers.PAYERegSpec
import models._
import models.validation.APIValidation
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.http.Status
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.test.FakeRequest
import repositories.RegistrationMongoRepository
import services._
import uk.gov.hmrc.auth.core.AuthorisedFunctions
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class RegistrationControllerSpec extends PAYERegSpec with RegistrationFixture {

  val mockRegistrationService = mock[RegistrationService]
  val mockSubmissionService   = mock[SubmissionService]
  val mockNotificationService = mock[NotificationService]
  val mockCounterService      = mock[IICounterService]

  implicit val system = ActorSystem("PR")
  implicit val materializer = ActorMaterializer()
  implicit val hc = HeaderCarrier()


  class Setup {
    val controller = new RegistrationCtrl {
      override val resourceConn         = mockRegistrationRepository
      override val registrationService  = mockRegistrationService
      override val submissionService    = mockSubmissionService
      override val notificationService  = mockNotificationService
      override val counterService       = mockCounterService
      override val authConnector        = mockAuthConnector
    }
  }

  override def beforeEach() {
    reset(mockRegistrationRepository)
    reset(mockAuthConnector)
  }

  case class TestModel(str: String, int: Int)
  implicit val format: Format[TestModel] = (
    (__ \ "str").format[String] and
    (__ \ "int").format[Int]
  )(TestModel.apply, unlift(TestModel.unapply))

  val testModel = TestModel(str = "testString", int = 616)
  val testJsonValid = Json.parse(
    """
      |{
      | "str" : "testString",
      | "int" : 616
      |}
    """.stripMargin
  )

  val testJsonInvalid = Json.parse(
    """
      |{
      | "integer" : 123
      |}
    """.stripMargin
  )

  val regId = "AC123456"
  val testInternalId = "testInternalID"

  "Calling newPAYERegistration" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      AuthorisationMocks.mockNotAuthenticated()

      val response = controller.newPAYERegistration(regId)(FakeRequest().withBody(Json.toJson[String]("NNASD9789F")))

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is logged in but no internalId" in new Setup {
      AuthorisationMocks.mockAuthenticatedNoInternalId

      val response = controller.newPAYERegistration(regId)(FakeRequest().withBody(Json.toJson[String]("NNASD9789F")))

      status(response) shouldBe Status.FORBIDDEN
    }

    "return a PAYERegistration for a successful creation" in new Setup {
      AuthorisationMocks.mockAuthenticated(testInternalId)

      when(mockRegistrationService.createNewPAYERegistration(ArgumentMatchers.contains(regId), ArgumentMatchers.contains("NNASD9789F"), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(validRegistration))

      val response = controller.newPAYERegistration(regId)(FakeRequest().withBody(Json.toJson[String]("NNASD9789F")))

      status(response) shouldBe Status.OK
    }
  }

  "Calling getPAYERegistration" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.fetchPAYERegistration(ArgumentMatchers.contains(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      val response = controller.getPAYERegistration(regId)(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.fetchPAYERegistration(ArgumentMatchers.contains(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validRegistration)))

      val response = controller.getPAYERegistration(regId)(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling getCompanyDetails" should {
    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getCompanyDetails(ArgumentMatchers.contains(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(validRegistration.companyDetails))

      val response = controller.getCompanyDetails(regId)(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling upsertCompanyDetails" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertCompanyDetails(ArgumentMatchers.contains(regId), ArgumentMatchers.any[CompanyDetails]())(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      val response = controller.upsertCompanyDetails(regId)(
        FakeRequest()
          .withBody(
            Json.toJson[CompanyDetails](validCompanyDetails)(CompanyDetails.format(APIValidation))
          )
      )

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Bad Request response if the Company Details are badly formatted" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertCompanyDetails(ArgumentMatchers.contains(regId), ArgumentMatchers.any[CompanyDetails]())(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new RegistrationFormatException("tstMessage")))

      val response = await(controller.upsertCompanyDetails(regId)(
        FakeRequest()
          .withBody(
            Json.toJson[CompanyDetails](validCompanyDetails)(CompanyDetails.format(APIValidation))
          )
        )
      )

      status(response) shouldBe Status.BAD_REQUEST
      bodyOf(response) shouldBe "tstMessage"
    }

    "return an OK response for a valid upsert" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertCompanyDetails(ArgumentMatchers.contains(regId), ArgumentMatchers.any[CompanyDetails]())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(validCompanyDetails))

      val response = controller.upsertCompanyDetails(regId)(
        FakeRequest()
          .withBody(
            Json.toJson[CompanyDetails](validCompanyDetails)(CompanyDetails.format(APIValidation))
          )
      )

      status(response) shouldBe Status.OK
    }
  }

  "Calling getEmployment" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getEmployment(ArgumentMatchers.contains(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      val response = controller.getEmployment(regId)(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getEmployment(ArgumentMatchers.contains(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(validRegistration.employment))

      val response = controller.getEmployment(regId)(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling upsertEmployment" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {

      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertEmployment(ArgumentMatchers.contains(regId), ArgumentMatchers.any[Employment]())(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      val response = controller.upsertEmployment(regId)(FakeRequest().withBody(Json.toJson[Employment](validEmployment)(Employment.format(APIValidation))))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return an OK response for a valid upsert" in new Setup {

      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertEmployment(ArgumentMatchers.contains(regId), ArgumentMatchers.any[Employment]())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(validEmployment))

      val response = controller.upsertEmployment(regId)(FakeRequest().withBody(Json.toJson[Employment](validEmployment)(Employment.format(APIValidation))))

      status(response) shouldBe Status.OK
    }
  }

  "Calling getDirectors" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getDirectors(ArgumentMatchers.contains(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Seq.empty))

      val response = controller.getDirectors(regId)(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getDirectors(ArgumentMatchers.contains(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(validRegistration.directors))

      val response = controller.getDirectors(regId)(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling upsertDirectors" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {

      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertDirectors(ArgumentMatchers.contains(regId), ArgumentMatchers.any[Seq[Director]]())(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      val response = controller.upsertDirectors(regId)(FakeRequest().withBody(Json.toJson[Seq[Director]](validDirectors)(Director.directorSequenceWriter(APIValidation))))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Bad Request response if there are no NINOs completed in the directors list" in new Setup {

      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertDirectors(ArgumentMatchers.contains(regId), ArgumentMatchers.any[Seq[Director]]())(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new RegistrationFormatException("test message")))

      val response = await(controller.upsertDirectors(regId)(FakeRequest().withBody(Json.toJson[Seq[Director]](validDirectors)(Director.directorSequenceWriter(APIValidation)))))

      status(response) shouldBe Status.BAD_REQUEST
      bodyOf(response) shouldBe "test message"
    }

    "return an OK response for a valid upsert" in new Setup {

      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertDirectors(ArgumentMatchers.contains(regId), ArgumentMatchers.any[Seq[Director]]())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(validDirectors))

      val response = controller.upsertDirectors(regId)(FakeRequest().withBody(Json.toJson[Seq[Director]](validDirectors)(Director.directorSequenceWriter(APIValidation))))

      status(response) shouldBe Status.OK
    }
  }

  "Calling getSICCodes" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getSICCodes(ArgumentMatchers.contains(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Seq.empty))

      val response = controller.getSICCodes(regId)(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getSICCodes(ArgumentMatchers.contains(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(validRegistration.sicCodes))

      val response = controller.getSICCodes(regId)(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling upsertSICCodes" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {

      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertSICCodes(ArgumentMatchers.contains(regId), ArgumentMatchers.any[Seq[SICCode]]())(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      val response = controller.upsertSICCodes(regId)(FakeRequest().withBody(Json.toJson[Seq[SICCode]](validSICCodes)))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return an OK response for a valid upsert" in new Setup {

      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertSICCodes(ArgumentMatchers.contains(regId), ArgumentMatchers.any[Seq[SICCode]]())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(validSICCodes))

      val response = controller.upsertSICCodes(regId)(FakeRequest().withBody(Json.toJson[Seq[SICCode]](validSICCodes)))

      status(response) shouldBe Status.OK
    }
  }

  "Calling getPAYEContact" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getPAYEContact(ArgumentMatchers.contains(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      val response = controller.getPAYEContact(regId)(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getPAYEContact(ArgumentMatchers.contains(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(validRegistration.payeContact))

      val response = controller.getPAYEContact(regId)(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling upsertPAYEContact" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertPAYEContact(ArgumentMatchers.contains(regId), ArgumentMatchers.any[PAYEContact]())(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      val response = controller.upsertPAYEContact(regId)(FakeRequest().withBody(Json.toJson[PAYEContact](validPAYEContact)(PAYEContact.format(APIValidation))))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Bad Request response if there is no contact method provided in the request" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertPAYEContact(ArgumentMatchers.contains(regId), ArgumentMatchers.any[PAYEContact]())(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new RegistrationFormatException("contact exception msg")))

      val response = await(controller.upsertPAYEContact(regId)(FakeRequest().withBody(Json.toJson[PAYEContact](validPAYEContact)(PAYEContact.format(APIValidation)))))

      status(response) shouldBe Status.BAD_REQUEST
      bodyOf(response) shouldBe "contact exception msg"
    }

    "return an OK response for a valid upsert" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertPAYEContact(ArgumentMatchers.contains(regId), ArgumentMatchers.any[PAYEContact]())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(validPAYEContact))

      val response = controller.upsertPAYEContact(regId)(FakeRequest().withBody(Json.toJson[PAYEContact](validPAYEContact)(PAYEContact.format(APIValidation))))

      status(response) shouldBe Status.OK
    }
  }

  "Calling getCompletionCapacity" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getCompletionCapacity(ArgumentMatchers.contains(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      val response = controller.getCompletionCapacity(regId)(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getCompletionCapacity(ArgumentMatchers.contains(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(validRegistration.completionCapacity))

      val response = controller.getCompletionCapacity(regId)(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "Calling upsertCompletionCapacity" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertCompletionCapacity(ArgumentMatchers.contains(regId), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      val response = controller.upsertCompletionCapacity(regId)(FakeRequest().withBody(Json.toJson[String]("Director")))

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a Bad Request response if completion capacity is incorrectly formatted" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertCompletionCapacity(ArgumentMatchers.contains(regId), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new RegistrationFormatException("errMessage")))

      val response = await(controller.upsertCompletionCapacity(regId)(FakeRequest().withBody(Json.toJson[String]("Director"))))

      status(response) shouldBe Status.BAD_REQUEST
      bodyOf(response) shouldBe "errMessage"
    }

    "return an OK response for a valid upsert" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertCompletionCapacity(ArgumentMatchers.contains(regId), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful("Director"))

      val response = controller.upsertCompletionCapacity(regId)(FakeRequest().withBody(Json.toJson[String]("Director")))

      status(response) shouldBe Status.OK
    }
  }

  "Calling submitPAYERegistration" should {
    "return a BadRequest response when the Submission Service can't make a DES submission" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockSubmissionService.submitToDes(ArgumentMatchers.contains(regId))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new EmploymentDetailsNotDefinedException("tst message")))

      val response = await(controller.submitPAYERegistration(regId)(FakeRequest()))

      status(response) shouldBe Status.BAD_REQUEST
      bodyOf(response) shouldBe "Registration was submitted without full data: tst message"
    }

    "return an Ok response with acknowledgement reference for a valid submit" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockSubmissionService.submitToDes(ArgumentMatchers.contains(regId))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.successful("BRPY00000000001"))

      val response = controller.submitPAYERegistration(regId)(FakeRequest())

      status(response) shouldBe Status.OK
      jsonBodyOf(await(response)) shouldBe Json.toJson("BRPY00000000001")
    }
  }

  "Calling getAcknowledgementReference" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getAcknowledgementReference(ArgumentMatchers.contains(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      val response = controller.getAcknowledgementReference(regId)(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getAcknowledgementReference(ArgumentMatchers.contains(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some("TESTBRPY001")))

      val response = controller.getAcknowledgementReference(regId)(FakeRequest())

      status(response) shouldBe Status.OK
    }
  }

  "getEligibility" should {
    "return an OK" when {
      "the users eligibility has been found" in new Setup {
        AuthorisationMocks.mockAuthorised(regId, testInternalId)

        when(mockRegistrationService.getEligibility(ArgumentMatchers.eq(regId))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(Eligibility(false, false))))

        val result = controller.getEligibility(regId)(FakeRequest())
        status(result) shouldBe Status.OK
      }
    }

    "return a NOT FOUND" when {
      "the eligibility cannot be found" in new Setup {
        AuthorisationMocks.mockAuthorised(regId, testInternalId)

        when(mockRegistrationService.getEligibility(ArgumentMatchers.eq(regId))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        val result = controller.getEligibility(regId)(FakeRequest())
        status(result) shouldBe Status.NOT_FOUND
      }
    }
  }

  "updateEligibility" should {
    "return an OK" when {
      "the eligibility has been updated" in new Setup {
        AuthorisationMocks.mockAuthorised(regId, testInternalId)

        when(mockRegistrationService.updateEligibility(ArgumentMatchers.eq(regId), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Eligibility(false, false)))

        val result = controller.updateEligibility(regId)(FakeRequest().withBody(Json.toJson(Eligibility(false, false))))
        status(result) shouldBe Status.OK
      }
    }

    "return a NOT FOUND" when {
      "the reg document cannot found against the reg id" in new Setup {
        AuthorisationMocks.mockAuthorised(regId, testInternalId)

        when(mockRegistrationService.updateEligibility(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.failed(new MissingRegDocument(regId)))

        val result = controller.updateEligibility(regId)(FakeRequest().withBody(Json.toJson(Eligibility(false, false))))
        status(result) shouldBe Status.NOT_FOUND
      }
    }
  }

  "updateRegistrationWithEmpRef" should {
    "return an OK" when {
      "the reg doc has been updated with the emp ref" in new Setup {
        val testNotification = EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "04")
        val request = FakeRequest().withBody(Json.toJson(testNotification))

        when(mockNotificationService.processNotification(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(testNotification))

        val result = controller.updateRegistrationWithEmpRef("testAckRef")(request)
        status(result) shouldBe Status.OK
      }
    }
  }

  "Calling getDocumentStatus" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getStatus(ArgumentMatchers.contains(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      val response = controller.getDocumentStatus(regId)(FakeRequest())

      status(response) shouldBe Status.NOT_FOUND
    }
  }

  "Calling deletePAYERegistration" should {
    "return an Ok response if the document has been deleted" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.deletePAYERegistration(ArgumentMatchers.anyString(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      val response = controller.deletePAYERegistration(regId)(FakeRequest())

      status(response) shouldBe Status.OK
    }

    "return an InternalServerError response if there was a mongo problem" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.deletePAYERegistration(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(false))

      val response = controller.deletePAYERegistration(regId)(FakeRequest())

      status(response) shouldBe Status.INTERNAL_SERVER_ERROR
    }

    "return a Pre condition failed response if the document status is not rejected" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.deletePAYERegistration(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new UnmatchedStatusException))

      val response = controller.deletePAYERegistration(regId)(FakeRequest())

      status(response) shouldBe Status.PRECONDITION_FAILED
    }
  }

  "Calling processIncorporationData" should {
    def incorpUpdate(status: String) = {
      s"""
         |{
         |  "SCRSIncorpStatus": {
         |    "IncorpSubscriptionKey" : {
         |      "subscriber" : "SCRS",
         |      "discriminator" : "PAYE",
         |      "transactionId" : "NN1234"
         |    },
         |    "SCRSIncorpSubscription" : {
         |      "callbackUrl" : "scrs-incorporation-update-listener.service/incorp-updates/incorp-status-update"
         |    },
         |    "IncorpStatusEvent": {
         |      "status": "$status",
         |      "crn":"OC123456",
         |      "incorporationDate":"2000-12-12",
         |      "timestamp" : ${Json.toJson(LocalDate.of(2017, 12, 21))}
         |    }
         |  }
         |}
        """.stripMargin
    }

    val jsonIncorpStatusUpdate = Json.parse(incorpUpdate("accepted"))

    "return a 500 response when the registration we try to incorporate is in invalid status and the II call count is < config value" in new Setup {
      when(mockRegistrationService.fetchPAYERegistrationByTransactionID(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.invalid))))

      when(mockSubmissionService.submitTopUpToDES(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.failed(new RegistrationInvalidStatus(validRegistration.registrationID, PAYEStatus.invalid.toString)))

      when(mockCounterService.maxIICounterCount).thenReturn(2)

      when(mockCounterService.updateIncorpCount(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(false))

      val response = controller.processIncorporationData(FakeRequest().withBody(Json.toJson(jsonIncorpStatusUpdate)))
      status(response) shouldBe Status.INTERNAL_SERVER_ERROR
    }

    "return a 200 response when the registration we try to incorporate is in invalid status and the II call count is > the config value" in new Setup {
      when(mockRegistrationService.fetchPAYERegistrationByTransactionID(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.invalid))))

      when(mockSubmissionService.submitTopUpToDES(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.failed(new RegistrationInvalidStatus(validRegistration.registrationID, PAYEStatus.invalid.toString)))

      when(mockCounterService.maxIICounterCount).thenReturn(2)

      when(mockCounterService.updateIncorpCount(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      val response = controller.processIncorporationData(FakeRequest().withBody(Json.toJson(jsonIncorpStatusUpdate)))
      status(response) shouldBe Status.OK
    }

    "return a 200 response when the registration we try to incorporate is in acknowledge status" in new Setup {
      when(mockRegistrationService.fetchPAYERegistrationByTransactionID(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.acknowledged))))

      when(mockSubmissionService.submitTopUpToDES(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.failed(new ErrorRegistrationException(validRegistration.registrationID, PAYEStatus.acknowledged.toString)))

      val response = controller.processIncorporationData(FakeRequest().withBody(Json.toJson(jsonIncorpStatusUpdate)))
      status(response) shouldBe Status.OK
    }

    "return a 200 response when the registration we try to incorporate is in rejected status" in new Setup {
      when(mockRegistrationService.fetchPAYERegistrationByTransactionID(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.rejected))))

      when(mockSubmissionService.submitTopUpToDES(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.failed(new ErrorRegistrationException(validRegistration.registrationID, PAYEStatus.rejected.toString)))

      val response = controller.processIncorporationData(FakeRequest().withBody(Json.toJson(jsonIncorpStatusUpdate)))
      status(response) shouldBe Status.OK
    }

    "return a 200 response when the registration we try to incorporate is in cancelled status" in new Setup {
      when(mockRegistrationService.fetchPAYERegistrationByTransactionID(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.cancelled))))

      when(mockSubmissionService.submitTopUpToDES(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.failed(new ErrorRegistrationException(validRegistration.registrationID, PAYEStatus.cancelled.toString)))

      val response = controller.processIncorporationData(FakeRequest().withBody(Json.toJson(jsonIncorpStatusUpdate)))
      status(response) shouldBe Status.OK
    }

    "return a 500 response when the mongo retrieve failed" in new Setup {
      when(mockRegistrationService.fetchPAYERegistrationByTransactionID(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new RetrieveFailed(validRegistration.registrationID)))

      val response = controller.processIncorporationData(FakeRequest().withBody(Json.toJson(jsonIncorpStatusUpdate)))
      status(response) shouldBe Status.INTERNAL_SERVER_ERROR

    }

    "return a 500 response when the mongo update failed" in new Setup {
      when(mockRegistrationService.fetchPAYERegistrationByTransactionID(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.held))))

      when(mockSubmissionService.submitTopUpToDES(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.failed(new UpdateFailed(validRegistration.registrationID, "Registration status")))

      val response = controller.processIncorporationData(FakeRequest().withBody(Json.toJson(jsonIncorpStatusUpdate)))
      status(response) shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }

  "calling registrationInvalidStatusHandler" should {

    "return a 500 response when the error is an invalid status and the II call count is < config value" in new Setup {
      when(mockCounterService.updateIncorpCount(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(false))

      val errorStatus = RegistrationInvalidStatus(validRegistration.registrationID,PAYEStatus.draft.toString)

      val response = controller.registrationInvalidStatusHandler(errorStatus,"NN1234")

      status(response) shouldBe Status.INTERNAL_SERVER_ERROR
    }

    "return a 500 response when error is an invalid status and an UpdateFailed Error is Encountered" in new Setup {
      when(mockCounterService.updateIncorpCount(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new UpdateFailed(validRegistration.registrationID, "IICounter")))

      val errorStatus = RegistrationInvalidStatus(validRegistration.registrationID,PAYEStatus.draft.toString)

      val response = controller.registrationInvalidStatusHandler(errorStatus,"NN1234")

      status(response) shouldBe Status.INTERNAL_SERVER_ERROR
    }

    "return a 200 response when the error is an invalid status and the II call count is > config value" in new Setup {
      when(mockCounterService.updateIncorpCount(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      val errorStatus = RegistrationInvalidStatus(validRegistration.registrationID,PAYEStatus.draft.toString)

      val response = controller.registrationInvalidStatusHandler(errorStatus,"NN1234")

      status(response) shouldBe Status.OK
    }
  }
}
