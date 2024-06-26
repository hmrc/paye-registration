/*
 * Copyright 2024 HM Revenue & Customs
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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{ActorMaterializer, Materializer}
import auth.CryptoSCRS
import common.exceptions.DBExceptions.{MissingRegDocument, RetrieveFailed, UpdateFailed}
import common.exceptions.RegistrationExceptions.{EmploymentDetailsNotDefinedException, RegistrationFormatException, UnmatchedStatusException}
import common.exceptions.SubmissionExceptions.{ErrorRegistrationException, RegistrationInvalidStatus}
import enums.{Employing, PAYEStatus}
import fixtures.RegistrationFixture
import helpers.PAYERegSpec
import models._
import models.validation.APIValidation
import org.mockito.ArgumentMatchers.{any, anyString, contains}
import org.mockito.Mockito._
import play.api.Configuration
import play.api.http.Status
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.RegistrationMongoRepository
import services._
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RegistrationControllerSpec extends PAYERegSpec with RegistrationFixture {

  val mockRegistrationService = mock[RegistrationService]
  val mockSubmissionService = mock[SubmissionService]
  val mockNotificationService = mock[NotificationService]
  val mockCounterService = mock[IICounterService]


  implicit val system: ActorSystem = ActorSystem("PR")
  implicit val materializer: Materializer = Materializer(system)
  implicit val hc: HeaderCarrier = HeaderCarrier()


  class Setup {
    val controller = new RegistrationController(
      mockRegistrationService,
      mockSubmissionService,
      mockNotificationService,
      mockCounterService,
      mockCrypto,
      mockAuthConnector,
      stubControllerComponents()
    ) {
      override val resourceConn: RegistrationMongoRepository = mockRegistrationRepository
    }
  }

  override def beforeEach() {
    reset(mockRegistrationRepository)
    reset(mockAuthConnector)
  }

  val currentDate = LocalDate.now

  case class TestModel(str: String, int: Int)

  implicit val format: Format[TestModel] = (
    (__ \ "str").format[String] and
      (__ \ "int").format[Int]
    ) (TestModel.apply, unlift(TestModel.unapply))

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
  val empInfo = EmploymentInfo(
    employees = Employing.alreadyEmploying,
    firstPaymentDate = currentDate,
    construction = true,
    subcontractors = true,
    companyPension = Some(true)
  )
  val jsonEmpInfo = Json.obj(
    "employees" -> "alreadyEmploying",
    "firstPaymentDate" -> currentDate,
    "construction" -> true,
    "subcontractors" -> true,
    "companyPension" -> true
  )

  val regId = "AC123456"
  val testInternalId = "testInternalID"

  "Calling newPAYERegistration" should {
    "return a Forbidden response if the user is not logged in" in new Setup {
      AuthorisationMocks.mockNotAuthenticated()

      val response = controller.newPAYERegistration(regId)(FakeRequest().withBody(Json.toJson[String]("NNASD9789F")))

      status(response) mustBe Status.FORBIDDEN
    }

    "return a Forbidden response if the user is logged in but no internalId" in new Setup {
      AuthorisationMocks.mockAuthenticatedNoInternalId

      val response = controller.newPAYERegistration(regId)(FakeRequest().withBody(Json.toJson[String]("NNASD9789F")))

      status(response) mustBe Status.FORBIDDEN
    }

    "return a PAYERegistration for a successful creation" in new Setup {
      AuthorisationMocks.mockAuthenticated(testInternalId)

      when(mockRegistrationService.createNewPAYERegistration(contains(regId), contains("NNASD9789F"), any()))
        .thenReturn(Future.successful(validRegistration))

      val response = controller.newPAYERegistration(regId)(FakeRequest().withBody(Json.toJson[String]("NNASD9789F")))

      status(response) mustBe Status.OK
    }
  }

  "Calling getPAYERegistration" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.fetchPAYERegistration(contains(regId)))
        .thenReturn(Future.successful(None))

      val response = controller.getPAYERegistration(regId)(FakeRequest())

      status(response) mustBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.fetchPAYERegistration(contains(regId)))
        .thenReturn(Future.successful(Some(validRegistration)))

      val response = controller.getPAYERegistration(regId)(FakeRequest())

      status(response) mustBe Status.OK
    }
  }

  "Calling getCompanyDetails" should {
    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getCompanyDetails(contains(regId)))
        .thenReturn(Future.successful(validRegistration.companyDetails))

      val response = controller.getCompanyDetails(regId)(FakeRequest())

      status(response) mustBe Status.OK
    }
  }

  "Calling upsertCompanyDetails" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertCompanyDetails(contains(regId), any[CompanyDetails]()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      val response = controller.upsertCompanyDetails(regId)(
        FakeRequest()
          .withBody(
            Json.toJson[CompanyDetails](validCompanyDetails)(CompanyDetails.format(APIValidation))
          )
      )

      status(response) mustBe Status.NOT_FOUND
    }

    "return a Bad Request response if the Company Details are badly formatted" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertCompanyDetails(contains(regId), any[CompanyDetails]()))
        .thenReturn(Future.failed(new RegistrationFormatException("tstMessage")))

      val response = controller.upsertCompanyDetails(regId)(
        FakeRequest()
          .withBody(
            Json.toJson[CompanyDetails](validCompanyDetails)(CompanyDetails.format(APIValidation))
          )
      )

      status(response) mustBe Status.BAD_REQUEST
      contentAsString(response) mustBe "tstMessage"
    }

    "return an OK response for a valid upsert" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertCompanyDetails(contains(regId), any[CompanyDetails]()))
        .thenReturn(Future.successful(validCompanyDetails))

      val response = controller.upsertCompanyDetails(regId)(
        FakeRequest()
          .withBody(
            Json.toJson[CompanyDetails](validCompanyDetails)(CompanyDetails.format(APIValidation))
          )
      )

      status(response) mustBe Status.OK
    }
  }
  "Calling getEmploymentInfo" should {
    val empInfo = EmploymentInfo(Employing.alreadyEmploying, LocalDate.of(2018, 4, 9), true, true, Some(true))
    val jsonEmpInfo = Json.parse(
      """|{
         |   "employees": "alreadyEmploying",
         |   "firstPaymentDate": "2018-04-09",
         |   "construction": true,
         |   "subcontractors": true,
         |   "companyPension": true
         | }
      """.stripMargin).as[JsObject]
    "return 200 with the employmentInfo" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)
      when(mockRegistrationService.getEmploymentInfo(any()))
        .thenReturn(Future.successful(Some(empInfo)))

      val res = controller.getEmploymentInfo(regId)(FakeRequest())
      status(res) mustBe 200
      contentAsJson(res).as[JsObject] mustBe jsonEmpInfo
    }
    "return 404 if reg doc is missing" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)
      when(mockRegistrationService.getEmploymentInfo(any())).
        thenReturn(Future.failed(new MissingRegDocument(regId)))

      val res = controller.getEmploymentInfo(regId)(FakeRequest())
      status(res) mustBe 404
    }
    "return 204 if block does not exist but reg doc does exist" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)
      when(mockRegistrationService.getEmploymentInfo(any())).
        thenReturn(Future.successful(None))

      val res = controller.getEmploymentInfo(regId)(FakeRequest())
      status(res) mustBe 204
    }
    "return 403 if the user is not authorised" in new Setup {
      AuthorisationMocks.mockNotLoggedInOrAuthorised(regId)
      val res = controller.getEmploymentInfo(regId)(FakeRequest())
      status(res) mustBe 403
    }
  }
  "Calling upsertEmploymentInfo" should {
    val incorpDate = currentDate
    val apiFormatForTest = EmploymentInfo.format(APIValidation, Some(incorpDate))

    "return 200 with the employmentInfo" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getIncorporationDate(contains(regId))(any()))
        .thenReturn(Future.successful(Some(incorpDate)))

      when(mockRegistrationService.upsertEmploymentInfo(contains(regId), any[EmploymentInfo]()))
        .thenReturn(Future.successful(empInfo))

      val res = controller.upsertEmploymentInfo(regId)(FakeRequest().withBody(Json.toJson[EmploymentInfo](empInfo)(apiFormatForTest)))
      contentAsJson(res).as[JsObject] mustBe jsonEmpInfo
    }
    "return 404 if reg doc is missing" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getIncorporationDate(contains(regId))(any()))
        .thenReturn(Future.successful(Some(incorpDate)))

      when(mockRegistrationService.upsertEmploymentInfo(contains(regId), any[EmploymentInfo]()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      val res = controller.upsertEmploymentInfo(regId)(FakeRequest().withBody(Json.toJson[EmploymentInfo](empInfo)(apiFormatForTest)))
      status(res) mustBe 404
    }
    "return 403 when the user is not authorised" in new Setup {
      AuthorisationMocks.mockNotLoggedInOrAuthorised(regId)

      val res = controller.upsertEmploymentInfo(regId)(FakeRequest().withBody(Json.toJson[EmploymentInfo](empInfo)(apiFormatForTest)))
      status(res) mustBe 403
    }
  }

  "Calling getDirectors" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getDirectors(contains(regId)))
        .thenReturn(Future.successful(Seq.empty))

      val response = controller.getDirectors(regId)(FakeRequest())

      status(response) mustBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getDirectors(contains(regId)))
        .thenReturn(Future.successful(validRegistration.directors))

      val response = controller.getDirectors(regId)(FakeRequest())

      status(response) mustBe Status.OK
    }
  }

  "Calling upsertDirectors" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {

      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertDirectors(contains(regId), any[Seq[Director]]()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      val response = controller.upsertDirectors(regId)(FakeRequest().withBody(Json.toJson[Seq[Director]](validDirectors)(Director.directorSequenceWriter(APIValidation))))

      status(response) mustBe Status.NOT_FOUND
    }

    "return a Bad Request response if there are no NINOs completed in the directors list" in new Setup {

      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertDirectors(contains(regId), any[Seq[Director]]()))
        .thenReturn(Future.failed(new RegistrationFormatException("test message")))

      val response = controller.upsertDirectors(regId)(FakeRequest().withBody(Json.toJson[Seq[Director]](validDirectors)(Director.directorSequenceWriter(APIValidation))))

      status(response) mustBe Status.BAD_REQUEST
      contentAsString(response) mustBe "test message"
    }

    "return an OK response for a valid upsert" in new Setup {

      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertDirectors(contains(regId), any[Seq[Director]]()))
        .thenReturn(Future.successful(validDirectors))

      val response = controller.upsertDirectors(regId)(FakeRequest().withBody(Json.toJson[Seq[Director]](validDirectors)(Director.directorSequenceWriter(APIValidation))))

      status(response) mustBe Status.OK
    }
  }

  "Calling getSICCodes" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getSICCodes(contains(regId)))
        .thenReturn(Future.successful(Seq.empty))

      val response = controller.getSICCodes(regId)(FakeRequest())

      status(response) mustBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getSICCodes(contains(regId)))
        .thenReturn(Future.successful(validRegistration.sicCodes))

      val response = controller.getSICCodes(regId)(FakeRequest())

      status(response) mustBe Status.OK
    }
  }

  "Calling upsertSICCodes" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {

      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertSICCodes(contains(regId), any[Seq[SICCode]]()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      val response = controller.upsertSICCodes(regId)(FakeRequest().withBody(Json.toJson[Seq[SICCode]](validSICCodes)))

      status(response) mustBe Status.NOT_FOUND
    }

    "return an OK response for a valid upsert" in new Setup {

      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertSICCodes(contains(regId), any[Seq[SICCode]]()))
        .thenReturn(Future.successful(validSICCodes))

      val response = controller.upsertSICCodes(regId)(FakeRequest().withBody(Json.toJson[Seq[SICCode]](validSICCodes)))

      status(response) mustBe Status.OK
    }
  }

  "Calling getPAYEContact" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getPAYEContact(contains(regId)))
        .thenReturn(Future.successful(None))

      val response = controller.getPAYEContact(regId)(FakeRequest())

      status(response) mustBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getPAYEContact(contains(regId)))
        .thenReturn(Future.successful(validRegistration.payeContact))

      val response = controller.getPAYEContact(regId)(FakeRequest())

      status(response) mustBe Status.OK
    }
  }

  "Calling upsertPAYEContact" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertPAYEContact(contains(regId), any[PAYEContact]()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      val response = controller.upsertPAYEContact(regId)(FakeRequest().withBody(Json.toJson[PAYEContact](validPAYEContact)(PAYEContact.format(APIValidation))))

      status(response) mustBe Status.NOT_FOUND
    }

    "return a Bad Request response if there is no contact method provided in the request" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertPAYEContact(contains(regId), any[PAYEContact]()))
        .thenReturn(Future.failed(new RegistrationFormatException("contact exception msg")))

      val response = controller.upsertPAYEContact(regId)(FakeRequest().withBody(Json.toJson[PAYEContact](validPAYEContact)(PAYEContact.format(APIValidation))))

      status(response) mustBe Status.BAD_REQUEST
      contentAsString(response) mustBe "contact exception msg"
    }

    "return an OK response for a valid upsert" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertPAYEContact(contains(regId), any[PAYEContact]()))
        .thenReturn(Future.successful(validPAYEContact))

      val response = controller.upsertPAYEContact(regId)(FakeRequest().withBody(Json.toJson[PAYEContact](validPAYEContact)(PAYEContact.format(APIValidation))))

      status(response) mustBe Status.OK
    }
  }

  "Calling getCompletionCapacity" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getCompletionCapacity(contains(regId)))
        .thenReturn(Future.successful(None))

      val response = controller.getCompletionCapacity(regId)(FakeRequest())

      status(response) mustBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getCompletionCapacity(contains(regId)))
        .thenReturn(Future.successful(validRegistration.completionCapacity))

      val response = controller.getCompletionCapacity(regId)(FakeRequest())

      status(response) mustBe Status.OK
    }
  }

  "Calling upsertCompletionCapacity" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertCompletionCapacity(contains(regId), any())(any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      val response = controller.upsertCompletionCapacity(regId)(FakeRequest().withBody(Json.toJson[String]("Director")))

      status(response) mustBe Status.NOT_FOUND
    }

    "return a Bad Request response if completion capacity is incorrectly formatted" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertCompletionCapacity(contains(regId), any())(any()))
        .thenReturn(Future.failed(new RegistrationFormatException("errMessage")))

      val response = controller.upsertCompletionCapacity(regId)(FakeRequest().withBody(Json.toJson[String]("Director")))

      status(response) mustBe Status.BAD_REQUEST
      contentAsString(response) mustBe "errMessage"
    }

    "return an OK response for a valid upsert" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.upsertCompletionCapacity(contains(regId), any())(any()))
        .thenReturn(Future.successful("Director"))

      val response = controller.upsertCompletionCapacity(regId)(FakeRequest().withBody(Json.toJson[String]("Director")))

      status(response) mustBe Status.OK
    }
  }

  "Calling submitPAYERegistration" should {
    "return a BadRequest response when the Submission Service can't make a DES submission" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockSubmissionService.submitToDes(contains(regId))(any[HeaderCarrier](), any()))
        .thenReturn(Future.failed(new EmploymentDetailsNotDefinedException("tst message")))

      val response = controller.submitPAYERegistration(regId)(FakeRequest())

      status(response) mustBe Status.BAD_REQUEST
      contentAsString(response) mustBe "Registration was submitted without full data: tst message"
    }

    "return an Ok response with acknowledgement reference for a valid submit" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockSubmissionService.submitToDes(contains(regId))(any[HeaderCarrier](), any()))
        .thenReturn(Future.successful("BRPY00000000001"))

      val response = controller.submitPAYERegistration(regId)(FakeRequest())

      status(response) mustBe Status.OK
      contentAsJson(response) mustBe Json.toJson("BRPY00000000001")
    }
  }

  "Calling getAcknowledgementReference" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getAcknowledgementReference(contains(regId))(any()))
        .thenReturn(Future.successful(None))

      val response = controller.getAcknowledgementReference(regId)(FakeRequest())

      status(response) mustBe Status.NOT_FOUND
    }

    "return a PAYERegistration for a successful query" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getAcknowledgementReference(contains(regId))(any()))
        .thenReturn(Future.successful(Some("TESTBRPY001")))

      val response = controller.getAcknowledgementReference(regId)(FakeRequest())

      status(response) mustBe Status.OK
    }
  }

  "updateRegistrationWithEmpRef" should {
    "return an OK" when {
      "the reg doc has been updated with the emp ref" in new Setup {
        implicit val f: Format[EmpRefNotification] = EmpRefNotification.format(APIValidation, new CryptoSCRS(Configuration("json.encryption.key" -> "MTIzNDU2Nzg5MDEyMzQ1Ng==")))
        val testNotification = EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "04")
        val request = FakeRequest().withBody(Json.toJson(testNotification))

        when(mockNotificationService.processNotification(any(), any())(any()))
          .thenReturn(Future.successful(testNotification))

        val result = controller.updateRegistrationWithEmpRef("testAckRef")(request)
        status(result) mustBe Status.OK
      }
    }
  }

  "Calling getDocumentStatus" should {
    "return a Not Found response if there is no PAYE Registration for the user's ID" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.getStatus(contains(regId))(any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      val response = controller.getDocumentStatus(regId)(FakeRequest())

      status(response) mustBe Status.NOT_FOUND
    }
  }

  "Calling deletePAYERegistration" should {
    "return an Ok response if the document has been deleted" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.deletePAYERegistration(anyString(), any())(any()))
        .thenReturn(Future.successful(true))

      val response = controller.deletePAYERegistration(regId)(FakeRequest())

      status(response) mustBe Status.OK
    }

    "return an InternalServerError response if there was a mongo problem" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.deletePAYERegistration(any(), any())(any()))
        .thenReturn(Future.successful(false))

      val response = controller.deletePAYERegistration(regId)(FakeRequest())

      status(response) mustBe Status.INTERNAL_SERVER_ERROR
    }

    "return a Pre condition failed response if the document status is not rejected" in new Setup {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      when(mockRegistrationService.deletePAYERegistration(any(), any())(any()))
        .thenReturn(Future.failed(new UnmatchedStatusException))

      val response = controller.deletePAYERegistration(regId)(FakeRequest())

      status(response) mustBe Status.PRECONDITION_FAILED
    }
  }

  "Calling deletePAYERegistrationIncorpRejected" should {
    "return an Ok response if the document has been deleted" in new Setup {
      when(mockRegistrationService.deletePAYERegistration(anyString(), any())(any()))
        .thenReturn(Future.successful(true))

      val response = controller.deletePAYERegistrationIncorpRejected(regId)(FakeRequest())

      status(response) mustBe Status.OK
    }

    "return an InternalServerError response if there was a mongo problem" in new Setup {
      when(mockRegistrationService.deletePAYERegistration(any(), any())(any()))
        .thenReturn(Future.successful(false))

      val response = controller.deletePAYERegistrationIncorpRejected(regId)(FakeRequest())

      status(response) mustBe Status.INTERNAL_SERVER_ERROR
    }

    "return a Pre condition failed response if the document status is not draft, invalid or cancelled" in new Setup {
      when(mockRegistrationService.deletePAYERegistration(any(), any())(any()))
        .thenReturn(Future.failed(new UnmatchedStatusException))

      val response = controller.deletePAYERegistrationIncorpRejected(regId)(FakeRequest())

      status(response) mustBe Status.PRECONDITION_FAILED
    }

    "return a Not found response if the document status is not rejected" in new Setup {
      when(mockRegistrationService.deletePAYERegistration(any(), any())(any()))
        .thenReturn(Future.failed(new MissingRegDocument("foo")))

      val response = controller.deletePAYERegistrationIncorpRejected(regId)(FakeRequest())

      status(response) mustBe Status.NOT_FOUND
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
      when(mockRegistrationService.fetchPAYERegistrationByTransactionID(any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.invalid))))

      when(mockSubmissionService.submitTopUpToDES(any(), any())(any[HeaderCarrier]()))
        .thenReturn(Future.failed(new RegistrationInvalidStatus(validRegistration.registrationID, PAYEStatus.invalid.toString)))

      when(mockCounterService.maxIICounterCount).thenReturn(2)

      when(mockCounterService.updateIncorpCount(any())(any()))
        .thenReturn(Future.successful(false))

      val response = controller.processIncorporationData(FakeRequest().withBody(Json.toJson(jsonIncorpStatusUpdate)))
      status(response) mustBe Status.INTERNAL_SERVER_ERROR
    }

    "return a 200 response when the registration we try to incorporate is in invalid status and the II call count is > the config value" in new Setup {
      when(mockRegistrationService.fetchPAYERegistrationByTransactionID(any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.invalid))))

      when(mockSubmissionService.submitTopUpToDES(any(), any())(any[HeaderCarrier]()))
        .thenReturn(Future.failed(new RegistrationInvalidStatus(validRegistration.registrationID, PAYEStatus.invalid.toString)))

      when(mockCounterService.maxIICounterCount).thenReturn(2)

      when(mockCounterService.updateIncorpCount(any())(any()))
        .thenReturn(Future.successful(true))

      val response = controller.processIncorporationData(FakeRequest().withBody(Json.toJson(jsonIncorpStatusUpdate)))
      status(response) mustBe Status.OK
    }

    "return a 200 response when the registration we try to incorporate is in acknowledge status" in new Setup {
      when(mockRegistrationService.fetchPAYERegistrationByTransactionID(any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.acknowledged))))

      when(mockSubmissionService.submitTopUpToDES(any(), any())(any[HeaderCarrier]()))
        .thenReturn(Future.failed(new ErrorRegistrationException(validRegistration.registrationID, PAYEStatus.acknowledged.toString)))

      val response = controller.processIncorporationData(FakeRequest().withBody(Json.toJson(jsonIncorpStatusUpdate)))
      status(response) mustBe Status.OK
    }

    "return a 200 response when the registration we try to incorporate is in rejected status" in new Setup {
      when(mockRegistrationService.fetchPAYERegistrationByTransactionID(any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.rejected))))

      when(mockSubmissionService.submitTopUpToDES(any(), any())(any[HeaderCarrier]()))
        .thenReturn(Future.failed(new ErrorRegistrationException(validRegistration.registrationID, PAYEStatus.rejected.toString)))

      val response = controller.processIncorporationData(FakeRequest().withBody(Json.toJson(jsonIncorpStatusUpdate)))
      status(response) mustBe Status.OK
    }

    "return a 200 response when the registration we try to incorporate is in cancelled status" in new Setup {
      when(mockRegistrationService.fetchPAYERegistrationByTransactionID(any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.cancelled))))

      when(mockSubmissionService.submitTopUpToDES(any(), any())(any[HeaderCarrier]()))
        .thenReturn(Future.failed(new ErrorRegistrationException(validRegistration.registrationID, PAYEStatus.cancelled.toString)))

      val response = controller.processIncorporationData(FakeRequest().withBody(Json.toJson(jsonIncorpStatusUpdate)))
      status(response) mustBe Status.OK
    }

    "return a 500 response when the mongo retrieve failed" in new Setup {
      when(mockRegistrationService.fetchPAYERegistrationByTransactionID(any()))
        .thenReturn(Future.failed(new RetrieveFailed(validRegistration.registrationID)))

      val response = controller.processIncorporationData(FakeRequest().withBody(Json.toJson(jsonIncorpStatusUpdate)))
      status(response) mustBe Status.INTERNAL_SERVER_ERROR

    }

    "return a 500 response when the mongo update failed" in new Setup {
      when(mockRegistrationService.fetchPAYERegistrationByTransactionID(any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.held))))

      when(mockSubmissionService.submitTopUpToDES(any(), any())(any[HeaderCarrier]()))
        .thenReturn(Future.failed(new UpdateFailed(validRegistration.registrationID, "Registration status")))

      val response = controller.processIncorporationData(FakeRequest().withBody(Json.toJson(jsonIncorpStatusUpdate)))
      status(response) mustBe Status.INTERNAL_SERVER_ERROR
    }
  }

  "calling registrationInvalidStatusHandler" should {

    "return a 500 response when the error is an invalid status and the II call count is < config value" in new Setup {
      when(mockCounterService.updateIncorpCount(any())(any()))
        .thenReturn(Future.successful(false))

      val errorStatus = RegistrationInvalidStatus(validRegistration.registrationID, PAYEStatus.draft.toString)

      val response = controller.registrationInvalidStatusHandler(errorStatus, "NN1234")

      status(response) mustBe Status.INTERNAL_SERVER_ERROR
    }

    "return a 500 response when error is an invalid status and an UpdateFailed Error is Encountered" in new Setup {
      when(mockCounterService.updateIncorpCount(any())(any()))
        .thenReturn(Future.failed(new UpdateFailed(validRegistration.registrationID, "IICounter")))

      val errorStatus = RegistrationInvalidStatus(validRegistration.registrationID, PAYEStatus.draft.toString)

      val response = controller.registrationInvalidStatusHandler(errorStatus, "NN1234")

      status(response) mustBe Status.INTERNAL_SERVER_ERROR
    }

    "return a 200 response when the error is an invalid status and the II call count is > config value" in new Setup {
      when(mockCounterService.updateIncorpCount(any())(any()))
        .thenReturn(Future.successful(true))

      val errorStatus = RegistrationInvalidStatus(validRegistration.registrationID, PAYEStatus.draft.toString)

      val response = controller.registrationInvalidStatusHandler(errorStatus, "NN1234")

      status(response) mustBe Status.OK
    }
  }

  "getRegistrationId" should {
    "return an Ok" in new Setup {
      when(mockRegistrationService.getRegistrationId(any()))
        .thenReturn(Future("testRegId"))

      val result = controller.getRegistrationId("txId")(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe JsString("testRegId")
    }

    "return a NotFound" in new Setup {
      when(mockRegistrationService.getRegistrationId(any()))
        .thenReturn(Future.failed(new MissingRegDocument("")))

      val result = controller.getRegistrationId("txId")(FakeRequest())
      status(result) mustBe NOT_FOUND
    }

    "return a Conflict" in new Setup {
      when(mockRegistrationService.getRegistrationId(any()))
        .thenReturn(Future.failed(new IllegalStateException))

      val result = controller.getRegistrationId("txId")(FakeRequest())
      status(result) mustBe CONFLICT
    }

    "return an InternalServerError" in new Setup {
      when(mockRegistrationService.getRegistrationId(any()))
        .thenReturn(Future.failed(new Exception))

      val result = controller.getRegistrationId("txId")(FakeRequest())
      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }
}
