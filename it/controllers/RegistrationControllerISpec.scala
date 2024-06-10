/*
 * Copyright 2023 HM Revenue & Customs
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

import auth.CryptoSCRS
import com.github.tomakehurst.wiremock.client.WireMock._
import com.kenshoo.play.metrics.Metrics
import enums.PAYEStatus
import fixtures.EmploymentInfoFixture
import helpers.DateHelper
import itutil.{IntegrationSpecBase, WiremockHelper}
import models._
import models.external.BusinessProfile
import models.validation.APIValidation
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Format, Json}
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import repositories.{IICounterMongoRepository, RegistrationMongoRepository, SequenceMongoRepository}
import uk.gov.hmrc.mongo.MongoComponent

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class RegistrationControllerISpec extends IntegrationSpecBase with EmploymentInfoFixture {

  lazy val mockcryptoSCRS = app.injector.instanceOf[CryptoSCRS]

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.auth.host" -> s"$mockHost",
    "microservice.services.auth.port" -> s"$mockPort",
    "microservice.services.des-stub.port" -> s"$mockPort",
    "microservice.services.des-stub.url" -> s"$mockHost",
    "microservice.services.des-service.url" -> s"$mockUrl",
    "microservice.services.des-service.uri" -> "business-registration/pay-as-you-earn",
    "microservice.services.des-service.top-up-uri" -> "business-incorporation/pay-as-you-earn",
    "application.router" -> "testOnlyDoNotUseInAppConf.Routes",
    "microservice.services.incorporation-information.host" -> s"$mockHost",
    "microservice.services.incorporation-information.port" -> s"$mockPort",
    "microservice.services.business-registration.host" -> s"$mockHost",
    "microservice.services.business-registration.port" -> s"$mockPort",
    "microservice.services.company-registration.host" -> s"$mockHost",
    "microservice.services.company-registration.port" -> s"$mockPort",
    "api.payeRestartURL" -> "testRestartURL",
    "api.payeCancelURL" -> "testCancelURL/:regID/del"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  lazy val mongoComponent = app.injector.instanceOf[MongoComponent]
  lazy val sConfig = app.injector.instanceOf[Configuration]

  class Setup {
    lazy val mockMetrics = app.injector.instanceOf[Metrics]
    val timestamp = "2017-01-01T00:00:00"
    lazy val mockDateHelper = new DateHelper {override def getTimestampString: String = timestamp}
    val repository = new RegistrationMongoRepository(mockMetrics, mockDateHelper, mongoComponent, sConfig, mockcryptoSCRS)
    val sequenceRepository = new SequenceMongoRepository(mongoComponent)
    val iiCounterRepository = app.injector.instanceOf[IICounterMongoRepository]

    await(repository.dropCollection)
    await(sequenceRepository.collection.drop().toFuture())
    await(sequenceRepository.ensureIndexes)
    await(iiCounterRepository.collection.drop().toFuture())
    await(iiCounterRepository.ensureIndexes)
  }

  val regId = "12345"
  val transactionID = "NN1234"
  val intId = "Int-xxx"
  val timestamp = "2017-01-01T00:00:00"
  val lastUpdate = "2017-05-09T07:58:35Z"
  val partialSubmissionTimestamp = "2017-05-10T07:58:35Z"
  val fullSubmissionTimestamp = "2017-05-11T07:58:35Z"
  val acknowledgedTimestamp = "2017-05-15T07:58:35Z"

  val submission = PAYERegistration(
    regId,
    transactionID,
    intId,
    Some("testAckRef"),
    None,
    None,
    timestamp,
    PAYEStatus.draft,
    Some("Director"),
    Some(
      CompanyDetails(
        "testCompanyName",
        Some("test"),
        Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), None),
        Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK")),
        DigitalContactDetails(Some("test@email.com"), Some("012345"), Some("543210"))
      )
    ),
    Seq(
      Director(
        Name(
          forename = Some("Thierry"),
          otherForenames = Some("Dominique"),
          surname = Some("Henry"),
          title = Some("Sir")
        ),
        Some("SR123456C")
      )
    ),
    Some(
      PAYEContact(
        contactDetails = PAYEContactDetails(
          name = "Thierry Henry",
          digitalContactDetails = DigitalContactDetails(
            Some("test@test.com"),
            Some("1234"),
            Some("4358475")
          )
        ),
        correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), None, Some("UK"))
      )
    ),
    Seq(
      SICCode(code = None, description = Some("consulting"))
    ),
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = None,
    employmentInfo = Some(validEmployment)
  )

  val processedSubmission = PAYERegistration(
    regId,
    transactionID,
    intId,
    Some("testAckRef"),
    None,
    None,
    timestamp,
    PAYEStatus.held,
    None,
    None,
    Nil,
    None,
    Nil,
    lastUpdate,
    partialSubmissionTimestamp = Some(partialSubmissionTimestamp),
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = None,
    employmentInfo = None
  )

  val rejectedSubmission = submission.copy(status = PAYEStatus.cancelled)

  val processedTopUpSubmission = PAYERegistration(
    regId,
    transactionID,
    intId,
    Some("testAckRef"),
    None,
    None,
    timestamp,
    PAYEStatus.submitted,
    None,
    None,
    Nil,
    None,
    Nil,
    lastUpdate,
    partialSubmissionTimestamp = Some(partialSubmissionTimestamp),
    fullSubmissionTimestamp = Some(fullSubmissionTimestamp),
    acknowledgedTimestamp = None,
    lastAction = None,
    employmentInfo = None
  )

  val businessProfile = BusinessProfile(regId, completionCapacity = None, language = "en")

  val crn = "OC123456"
  val accepted = "accepted"
  val rejected = "rejected"

  def incorpUpdate(status: String) = {
    s"""
       |{
       |  "SCRSIncorpStatus": {
       |    "IncorpSubscriptionKey" : {
       |      "subscriber" : "SCRS",
       |      "discriminator" : "PAYE",
       |      "transactionId" : "$transactionID"
       |    },
       |    "SCRSIncorpSubscription" : {
       |      "callbackUrl" : "scrs-incorporation-update-listener.service/incorp-updates/incorp-status-update"
       |    },
       |    "IncorpStatusEvent": {
       |      "status": "$status",
       |      "crn":"$crn",
       |      "incorporationDate":"2000-12-12",
       |      "timestamp" : ${Json.toJson(LocalDate.of(2017, 12, 21))}
       |    }
       |  }
       |}
        """.stripMargin
  }

  def incorpUpdateNoCRN(status: String) = {
    s"""
       |{
       |  "SCRSIncorpStatus": {
       |    "IncorpSubscriptionKey" : {
       |      "subscriber" : "SCRS",
       |      "discriminator" : "PAYE",
       |      "transactionId" : "$transactionID"
       |    },
       |    "SCRSIncorpSubscription" : {
       |      "callbackUrl" : "scrs-incorporation-update-listener.service/incorp-updates/incorp-status-update"
       |    },
       |    "IncorpStatusEvent": {
       |      "status": "$status",
       |      "incorporationDate":"2000-12-12",
       |      "timestamp" : ${Json.toJson(LocalDate.of(2017, 12, 21))}
       |    }
       |  }
       |}
        """.stripMargin
  }
  val jsonIncorpStatusUpdate = Json.parse(incorpUpdate(accepted))



  "incorporation-data" should {

    "return a 200 with a crn" in new Setup {
      setupSimpleAuthMocks()

      stubFor(post(urlMatching("/business-incorporation/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(200)
        )
      )

      await(client(s"/test-only/feature-flag/desServiceFeature/true").get())
      await(repository.collection.insertOne(processedSubmission).toFuture())

      val response = await(client(s"/incorporation-data").post(jsonIncorpStatusUpdate))
      response.status mustBe 200
      response.json mustBe Json.toJson(crn)

      verify(postRequestedFor(urlEqualTo("/business-incorporation/pay-as-you-earn"))
        .withRequestBody(equalToJson(Json.parse(
          s"""
             |{
             | "acknowledgementReference": "testAckRef",
             | "status": "Accepted",
             | "payAsYouEarn": {
             |  "crn": "OC123456"
             | }
             |}
          """.stripMargin).toString())
        )
      )

      verify(postRequestedFor(urlEqualTo("/write/audit"))
        .withRequestBody(equalToJson(Json.parse(
          s"""
             |{
             |  "auditSource" : "paye-registration",
             |  "auditType" : "payeRegistrationAdditionalData",
             |  "detail" : {
             |    "journeyId" : "$regId",
             |    "acknowledgementReference" : "testAckRef",
             |    "status" : "accepted",
             |    "payAsYouEarn" : {
             |       "crn" : "OC123456"
             |     }
             |  },
             |  "tags" : {
             |     "clientIP" : "-",
             |     "X-Session-ID" : "session-12345",
             |     "X-Request-ID" : "-",
             |     "clientPort" : "-",
             |     "transactionName" : "payeRegistrationAdditionalData"
             |  }
             |}
          """.stripMargin).toString(), true, true)
        )
      )

      val reg = await(repository.retrieveRegistration(regId))
      reg mustBe Some(processedTopUpSubmission.copy(lastUpdate = reg.get.lastUpdate, fullSubmissionTimestamp = reg.get.fullSubmissionTimestamp, lastAction = reg.get.lastAction))

      val regLastUpdate = mockDateHelper.getDateFromTimestamp(reg.get.lastUpdate)
      val submissionLastUpdate = mockDateHelper.getDateFromTimestamp(processedSubmission.lastUpdate)

      regLastUpdate.isAfter(submissionLastUpdate) mustBe true
      reg.get.fullSubmissionTimestamp.nonEmpty mustBe true
    }

    "return a 200 when Incorporation is rejected" in new Setup {

      setupSimpleAuthMocks()

      stubFor(post(urlMatching("/business-incorporation/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(200)
        )
      )

      await(repository.updateRegistration(processedSubmission))
      await(client(s"/test-only/feature-flag/desServiceFeature/true").get())

      val response = await(client(s"/incorporation-data").post(Json.parse(incorpUpdateNoCRN(rejected))))
      response.status mustBe 200
      response.json mustBe Json.toJson(None)

      verify(postRequestedFor(urlEqualTo("/business-incorporation/pay-as-you-earn"))
        .withRequestBody(equalToJson(Json.parse(
          s"""
             |{
             | "acknowledgementReference": "testAckRef",
             | "status": "Rejected"
             |}
          """.stripMargin).toString())
        )
      )

      verify(postRequestedFor(urlEqualTo("/write/audit"))
        .withRequestBody(equalToJson(Json.parse(
          s"""
             |{
             |  "auditSource" : "paye-registration",
             |  "auditType" : "incorporationFailure",
             |  "detail" : {
             |    "journeyId" : "$regId",
             |    "acknowledgementReference" : "testAckRef",
             |    "incorporationStatus" : "rejected"
             |  },
             |  "tags" : {
             |     "clientIP" : "-",
             |     "X-Session-ID" : "session-12345",
             |     "X-Request-ID" : "-",
             |     "clientPort" : "-",
             |     "transactionName" : "incorporationFailure"
             |   }
             |}
          """.stripMargin).toString(), true, true)
        )
      )

      val reg = await(repository.retrieveRegistration(regId))
      reg mustBe None
    }

    "return a 200 status when registration is not found" in new Setup {
      setupSimpleAuthMocks()

      stubFor(get(urlMatching(s"/company-registration/corporation-tax-registration/12345/corporation-tax-registration"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """{
                | "acknowledgementReferences" : {
                |   "ctUtr" : "testCtUtr"
                | }
                |}
                |""".stripMargin
            )
        )
      )

      val jsonIncorpStatusUpdate2 = Json.parse(
        s"""
           |{
           |  "IncorpSubscriptionKey" : {
           |    "subscriber" : "SCRS",
           |    "discriminator" : "PAYE",
           |    "transactionId" : "NN5678"
           |  },
           |  "SCRSIncorpSubscription" : {
           |      "callbackUrl" : "scrs-incorporation-update-listener.service/incorp-updates/incorp-status-update"
           |  },
           |  "IncorpStatusEvent": {
           |      "status": "accepted",
           |      "crn":"$crn",
           |      "incorporationDate":"2000-12-12",
           |      "timestamp" : ${Json.toJson(LocalDate.of(2017, 12, 21))}
           |  }
           |}
        """.stripMargin)

      await(repository.updateRegistration(processedSubmission))

      val response = await(client(s"/incorporation-data").post(jsonIncorpStatusUpdate2))
      response.status mustBe 200

      await(repository.retrieveRegistration(regId)) mustBe Some(processedSubmission)
    }

    "return a 200 status when registration is already submitted" in new Setup {
      setupSimpleAuthMocks()

      stubFor(get(urlMatching(s"/company-registration/corporation-tax-registration/12345/corporation-tax-registration"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """{
                | "acknowledgementReferences" : {
                |   "ctUtr" : "testCtUtr"
                | }
                |}
                |""".stripMargin
            )
        )
      )

      await(repository.updateRegistration(processedTopUpSubmission))
      await(client(s"/test-only/feature-flag/desServiceFeature/true").get())

      val response = await(client(s"/incorporation-data").post(jsonIncorpStatusUpdate))
      response.status mustBe 200

      await(repository.retrieveRegistration(regId)) mustBe Some(processedTopUpSubmission)
    }

    "return a 500 status when registration is not yet submitted with partial" in new Setup {
      setupSimpleAuthMocks()


      stubFor(get(urlMatching(s"/company-registration/corporation-tax-registration/12345/corporation-tax-registration"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """{
                | "acknowledgementReferences" : {
                |   "ctUtr" : "testCtUtr"
                | }
                |}
                |""".stripMargin
            )
        )
      )

      await(repository.updateRegistration(submission))
      await(client(s"/test-only/feature-flag/desServiceFeature/true").get())

      val response = await(client(s"/incorporation-data").post(jsonIncorpStatusUpdate))
      response.status mustBe 500

      await(repository.retrieveRegistration(regId)) mustBe Some(submission)
    }
  }

  "submitting a top up registration with DES stubbed out" should {

    "return a 200 with an ack ref" in new Setup {

      setupSimpleAuthMocks()

      stubFor(get(urlMatching(s"/company-registration/corporation-tax-registration/12345/corporation-tax-registration"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """{
                | "acknowledgementReferences" : {
                |   "ctUtr" : "testCtUtr"
                | }
                |}
                |""".stripMargin
            )
        )
      )

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(200)
        )
      )

      await(repository.updateRegistration(processedSubmission))
      await(client(s"/test-only/feature-flag/desServiceFeature/false").get())

      val response = await(client(s"/incorporation-data").post(jsonIncorpStatusUpdate))
      response.status mustBe 200
      response.json mustBe Json.toJson(crn)

      val reg = await(repository.retrieveRegistration(regId))
      reg mustBe Some(processedTopUpSubmission.copy(
        lastUpdate = reg.get.lastUpdate,
        fullSubmissionTimestamp = reg.get.fullSubmissionTimestamp,
        lastAction = reg.get.lastAction
      ))

      val regLastUpdate = mockDateHelper.getDateFromTimestamp(reg.get.lastUpdate)
      val submissionLastUpdate = mockDateHelper.getDateFromTimestamp(processedSubmission.lastUpdate)

      regLastUpdate.isAfter(submissionLastUpdate) mustBe true
      reg.get.fullSubmissionTimestamp.nonEmpty mustBe true
    }

    "return a 200 when Incorporation is rejected" in new Setup {

      setupSimpleAuthMocks()

      stubFor(get(urlMatching(s"/company-registration/corporation-tax-registration/12345/corporation-tax-registration"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """{
                | "acknowledgementReferences" : {
                |   "ctUtr" : "testCtUtr"
                | }
                |}
                |""".stripMargin
            )
        )
      )

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(200)
        )
      )

      await(repository.updateRegistration(processedSubmission))
      await(client(s"/test-only/feature-flag/desServiceFeature/false").get())

      val response = await(client(s"/incorporation-data").post(Json.parse(incorpUpdateNoCRN(rejected))))
      response.status mustBe 200
      val none: Option[String] = None
      response.json mustBe Json.toJson(none)

      val reg = await(repository.retrieveRegistration(regId))
      reg mustBe None
    }
  }

  "updateRegistrationWithEmpRef" should {
    "return an OK with a Json body" when {
      "the emp ref has been updated as APPROVED" in new Setup {
        setupSimpleAuthMocks()

        await(repository.updateRegistration(processedSubmission.copy(registrationConfirmation = None, acknowledgementReference = Some("ackRef"))))
        implicit val f: Format[EmpRefNotification] = EmpRefNotification.format(APIValidation, mockcryptoSCRS)
        val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "04"))

        val response = await(client("/registration-processed-confirmation?ackref=ackRef").post(testNotification))
        response.status mustBe 200
        response.json mustBe testNotification

        val reg = await(repository.retrieveRegistration(processedSubmission.registrationID)).get
        reg.registrationConfirmation mustBe Some(
          EmpRefNotification(
            empRef = Some("testEmpRef"),
            timestamp = "2017-01-01T12:00:00Z",
            status = "04"
          )
        )
        reg.status mustBe PAYEStatus.acknowledged
        reg.acknowledgedTimestamp.isDefined mustBe true
      }

      "the emp ref has been updated as APPROVED WITH CONDITIONS" in new Setup {
        setupSimpleAuthMocks()

        await(repository.updateRegistration(processedSubmission.copy(registrationConfirmation = None, acknowledgementReference = Some("ackRef"))))
        implicit val f: Format[EmpRefNotification] = EmpRefNotification.format(APIValidation, mockcryptoSCRS)
        val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "05"))

        val response = await(client("/registration-processed-confirmation?ackref=ackRef").post(testNotification))
        response.status mustBe 200
        response.json mustBe testNotification

        val reg = await(repository.retrieveRegistration(processedSubmission.registrationID)).get
        reg.registrationConfirmation mustBe Some(
          EmpRefNotification(
            empRef = Some("testEmpRef"),
            timestamp = "2017-01-01T12:00:00Z",
            status = "05"
          )
        )
        reg.status mustBe PAYEStatus.acknowledged
        reg.acknowledgedTimestamp.isDefined mustBe true
      }

      "the emp ref has been updated as REJECTED" in new Setup {
        setupSimpleAuthMocks()

        await(repository.updateRegistration(processedSubmission.copy(registrationConfirmation = None, acknowledgementReference = Some("ackRef"))))
        implicit val f: Format[EmpRefNotification] = EmpRefNotification.format(APIValidation, mockcryptoSCRS)
        val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "06"))

        val response = await(client("/registration-processed-confirmation?ackref=ackRef").post(testNotification))
        response.status mustBe 200
        response.json mustBe testNotification

        val reg = await(repository.retrieveRegistration(processedSubmission.registrationID)).get
        reg.registrationConfirmation mustBe Some(
          EmpRefNotification(
            empRef = Some("testEmpRef"),
            timestamp = "2017-01-01T12:00:00Z",
            status = "06"
          )
        )
        reg.status mustBe PAYEStatus.rejected
        reg.acknowledgedTimestamp.isDefined mustBe true
      }

      "the emp ref has been updated as REJECTED_UNDER_REVIEW_APPREAL" in new Setup {
        setupSimpleAuthMocks()

        await(repository.updateRegistration(processedSubmission.copy(registrationConfirmation = None, acknowledgementReference = Some("ackRef"))))
        implicit val f: Format[EmpRefNotification] = EmpRefNotification.format(APIValidation, mockcryptoSCRS)
        val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "07"))

        val response = await(client("/registration-processed-confirmation?ackref=ackRef").post(testNotification))
        response.status mustBe 200
        response.json mustBe testNotification

        val reg = await(repository.retrieveRegistration(processedSubmission.registrationID)).get
        reg.registrationConfirmation mustBe Some(
          EmpRefNotification(
            empRef = Some("testEmpRef"),
            timestamp = "2017-01-01T12:00:00Z",
            status = "07"
          )
        )
        reg.status mustBe PAYEStatus.rejected
        reg.acknowledgedTimestamp.isDefined mustBe true
      }
    }

    "the emp ref has been updated as REVOKED" in new Setup {
      setupSimpleAuthMocks()

      await(repository.updateRegistration(processedSubmission.copy(registrationConfirmation = None, acknowledgementReference = Some("ackRef"))))
      implicit val f: Format[EmpRefNotification] = EmpRefNotification.format(APIValidation, mockcryptoSCRS)
      val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "08"))

      val response = await(client("/registration-processed-confirmation?ackref=ackRef").post(testNotification))
      response.status mustBe 200
      response.json mustBe testNotification

      val reg = await(repository.retrieveRegistration(processedSubmission.registrationID)).get
      reg.registrationConfirmation mustBe Some(
        EmpRefNotification(
          empRef = Some("testEmpRef"),
          timestamp = "2017-01-01T12:00:00Z",
          status = "08"
        )
      )
      reg.status mustBe PAYEStatus.rejected
      reg.acknowledgedTimestamp.isDefined mustBe true
    }

    "the emp ref has been updated as REVOKED_UNDER_REVIEW_APPREAL" in new Setup {
      setupSimpleAuthMocks()

      await(repository.updateRegistration(processedSubmission.copy(registrationConfirmation = None, acknowledgementReference = Some("ackRef"))))
      implicit val f: Format[EmpRefNotification] = EmpRefNotification.format(APIValidation, mockcryptoSCRS)
      val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "09"))

      val response = await(client("/registration-processed-confirmation?ackref=ackRef").post(testNotification))
      response.status mustBe 200
      response.json mustBe testNotification

      val reg = await(repository.retrieveRegistration(processedSubmission.registrationID)).get
      reg.registrationConfirmation mustBe Some(
        EmpRefNotification(
          empRef = Some("testEmpRef"),
          timestamp = "2017-01-01T12:00:00Z",
          status = "09"
        )
      )
      reg.status mustBe PAYEStatus.rejected
      reg.acknowledgedTimestamp.isDefined mustBe true
    }

    "the emp ref has been updated as DEREGISTERED" in new Setup {
      setupSimpleAuthMocks()

      await(repository.updateRegistration(processedSubmission.copy(registrationConfirmation = None, acknowledgementReference = Some("ackRef"))))
      implicit val f: Format[EmpRefNotification] = EmpRefNotification.format(APIValidation, mockcryptoSCRS)
      val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "10"))

      val response = await(client("/registration-processed-confirmation?ackref=ackRef").post(testNotification))
      response.status mustBe 200
      response.json mustBe testNotification

      val reg = await(repository.retrieveRegistration(processedSubmission.registrationID)).get
      reg.registrationConfirmation mustBe Some(
        EmpRefNotification(
          empRef = Some("testEmpRef"),
          timestamp = "2017-01-01T12:00:00Z",
          status = "10"
        )
      )
      reg.status mustBe PAYEStatus.rejected
      reg.acknowledgedTimestamp.isDefined mustBe true
    }

    "return a not found" when {
      "a matching reg doc cannot be found" in new Setup {
        setupSimpleAuthMocks()
        implicit val f: Format[EmpRefNotification] = EmpRefNotification.format(APIValidation, mockcryptoSCRS)
        val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "04"))

        val response = await(client("/registration-processed-confirmation?ackref=invalidackref").post(testNotification))
        response.status mustBe 404

        await(repository.retrieveRegistrationByAckRef("invalidackref")) mustBe None
      }
    }
  }

  "getStatus" should {
    "return an OK with a full document status" in new Setup {
      val json = Json.parse(s"""{
                              |   "status": "acknowledged",
                              |   "lastUpdate": "$acknowledgedTimestamp",
                              |   "ackRef": "testAckRef",
                              |   "empref": "testEmpRef"
                              |}""".stripMargin)

      setupSimpleAuthMocks()

     val fudge = Json.toJson("testEmpRef")(mockcryptoSCRS.wts)
     val testNotification = EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "04")
     val doc = submission.copy(status = PAYEStatus.acknowledged, registrationConfirmation = Some(testNotification), acknowledgedTimestamp = Some(acknowledgedTimestamp))

      await(repository.updateRegistration(submission.copy(status = PAYEStatus.acknowledged, registrationConfirmation = Some(testNotification), acknowledgedTimestamp = Some(acknowledgedTimestamp))))

      val response = await(client(s"/$regId/status").get())
      response.status mustBe 200
      response.json mustBe json
    }

    "return an OK with a partial document status with cancelURL when status is draft, lastUpdate returns formCreationTimestamp" in new Setup {
      val json = Json.parse(s"""{
                               |   "status": "draft",
                               |   "lastUpdate": "$timestamp",
                               |   "cancelURL": "testCancelURL/$regId/del"
                               |}""".stripMargin)

      setupSimpleAuthMocks()

      await(repository.updateRegistration(submission.copy(acknowledgementReference = None)))

      val response = await(client(s"/$regId/status").get())
      response.status mustBe 200
      response.json mustBe json
    }

    "return an OK with a partial document status with cancelURL when status is invalid, lastUpdate returns formCreationTimestamp" in new Setup {
      val json = Json.parse(s"""{
                               |   "status": "invalid",
                               |   "lastUpdate": "$timestamp",
                               |   "cancelURL": "testCancelURL/$regId/del"
                               |}""".stripMargin)

      setupSimpleAuthMocks()

      await(repository.updateRegistration(submission.copy(acknowledgementReference = None, status = PAYEStatus.invalid)))

      val response = await(client(s"/$regId/status").get())
      response.status mustBe 200
      response.json mustBe json
    }

    "return an OK with a partial document status when status is cancelled, lastUpdate returns formCreationTimestamp" in new Setup {
      val json = Json.parse(s"""{
                               |   "status": "cancelled",
                               |   "lastUpdate": "$lastUpdate"
                               |}""".stripMargin)

      setupSimpleAuthMocks()

      await(repository.updateRegistration(submission.copy(acknowledgementReference = None, status = PAYEStatus.cancelled)))

      val response = await(client(s"/$regId/status").get())
      response.status mustBe 200
      response.json mustBe json
    }

    "return an OK with a partial document status when status is held, lastUpdate returns partialSubmissionTimestamp" in new Setup {
      val json = Json.parse(s"""{
                              |   "status": "held",
                              |   "lastUpdate": "$partialSubmissionTimestamp",
                              |   "ackRef": "testAckRef"
                              |}""".stripMargin)

      setupSimpleAuthMocks()

      await(repository.updateRegistration(processedSubmission))

      val response = await(client(s"/$regId/status").get())
      response.status mustBe 200
      response.json mustBe json
    }

    "return an OK with a partial document status when status is submitted, lastUpdate returns fullSubmissionTimestamp" in new Setup {
      val json = Json.parse(s"""{
                              |   "status": "submitted",
                              |   "lastUpdate": "$fullSubmissionTimestamp",
                              |   "ackRef": "testAckRef"
                              |}""".stripMargin)

      setupSimpleAuthMocks()

      await(repository.updateRegistration(processedTopUpSubmission))

      val response = await(client(s"/$regId/status").get())
      response.status mustBe 200
      response.json mustBe json
    }

    "return an OK with a partial document status with restartURL when status is rejected, lastUpdate returns acknowledgedTimestamp" in new Setup {
      val json = Json.parse(s"""{
                               |   "status": "rejected",
                               |   "lastUpdate": "$acknowledgedTimestamp",
                               |   "ackRef": "testAckRef",
                               |   "restartURL": "testRestartURL"
                               |}""".stripMargin)

      setupSimpleAuthMocks()

      await(repository.updateRegistration(submission.copy(status = PAYEStatus.rejected, acknowledgedTimestamp = Some(acknowledgedTimestamp))))

      val response = await(client(s"/$regId/status").get())
      response.status mustBe 200
      response.json mustBe json
    }

    "return a 404 error when the registration Id is invalid" in new Setup {
      setupSimpleAuthMocks()

      val response = await(client(s"/invalid234/status").get())
      response.status mustBe 404
    }
  }

  "deletePAYERegistration" should {
    "return an OK after deleting the document" in new Setup {
      setupSimpleAuthMocks()

      await(repository.updateRegistration(submission.copy(status = PAYEStatus.rejected, acknowledgedTimestamp = Some(acknowledgedTimestamp))))

      val response = await(client(s"/$regId/delete").delete())
      response.status mustBe 200

      await(repository.retrieveRegistration(submission.registrationID)) mustBe None
    }

    "return a PreconditionFailed response if the document status is not 'rejected'" in new Setup {
      setupSimpleAuthMocks()

      await(repository.updateRegistration(submission))

      val response = await(client(s"/$regId/delete").delete())
      response.status mustBe 412

      await(repository.retrieveRegistration(submission.registrationID)) mustBe Some(submission)
    }

    "return a NotFound trying deleting a non existing document" in new Setup {
      setupSimpleAuthMocks()

      await(repository.updateRegistration(submission.copy(status = PAYEStatus.rejected, acknowledgedTimestamp = Some(acknowledgedTimestamp))))

      val response = await(client(s"/invalidRegId/delete").delete())
      response.status mustBe 404
    }
  }
  "deletePAYERegistrationIncorpRejected" should {
    "return an OK after deleting the document" in new Setup {

      await(repository.updateRegistration(submission.copy(status = PAYEStatus.draft, acknowledgedTimestamp = Some(acknowledgedTimestamp))))

      val response = await(client(s"/$regId/delete-rejected-incorp").delete())
      response.status mustBe 200

      await(repository.retrieveRegistration(submission.registrationID)) mustBe None
    }

    "return a PreconditionFailed response if the document status is not draft or invalid'" in new Setup {

      await(repository.updateRegistration(submission.copy(status = PAYEStatus.rejected, acknowledgedTimestamp = Some(acknowledgedTimestamp))))

      val response = await(client(s"/$regId/delete-rejected-incorp").delete())
      response.status mustBe 412

      await(repository.retrieveRegistration(submission.registrationID)) mustBe Some(submission.copy(status = PAYEStatus.rejected, acknowledgedTimestamp = Some(acknowledgedTimestamp)))
    }

    "return a NotFound trying deleting a non existing document" in new Setup {

      await(repository.updateRegistration(submission.copy(status = PAYEStatus.rejected, acknowledgedTimestamp = Some(acknowledgedTimestamp))))

      val response = await(client(s"/invalidRegId/delete-rejected-incorp").delete())
      response.status mustBe 404
    }
  }
}
