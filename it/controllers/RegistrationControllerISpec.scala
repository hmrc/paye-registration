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

import com.github.tomakehurst.wiremock.client.WireMock._
import enums.PAYEStatus
import helpers.DateHelper
import itutil.{EncryptionHelper, IntegrationSpecBase, WiremockHelper}
import models._
import models.external.BusinessProfile
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WS
import play.api.{Application, Play}
import repositories.{RegistrationMongo, RegistrationMongoRepository, SequenceMongo, SequenceMongoRepository}
import services.MetricsService
import uk.gov.hmrc.crypto.CryptoWithKeysFromConfig

import scala.concurrent.ExecutionContext.Implicits.global

class RegistrationControllerISpec extends IntegrationSpecBase with EncryptionHelper {

  override lazy val crypto = CryptoWithKeysFromConfig(baseConfigKey = "mongo-encryption")

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

  private def client(path: String) = ws.url(s"http://localhost:$port/paye-registration/$path")
                                        .withFollowRedirects(false)
                                        .withHeaders(("X-Session-ID","session-12345"))

  private val regime = "paye"
  private val subscriber = "SCRS"

  class Setup {
    lazy val mockMetrics = app.injector.instanceOf[MetricsService]

    val mongo = new RegistrationMongo(mockMetrics)
    val sequenceMongo = new SequenceMongo()
    val repository: RegistrationMongoRepository = mongo.store
    val sequenceRepository: SequenceMongoRepository = sequenceMongo.store
    await(repository.drop)
    await(repository.ensureIndexes)
    await(sequenceRepository.drop)
    await(sequenceRepository.ensureIndexes)
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
    Some(Eligibility(false, false)),
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
    Some(
      Employment(
        employees = true,
        companyPension = Some(true),
        subcontractors = true,
        firstPaymentDate = LocalDate.of(2016, 12, 20)
      )
    ),
    Seq(
      SICCode(code = None, description = Some("consulting"))
    ),
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = None
  )

  val processedSubmission = PAYERegistration(
    regId,
    transactionID,
    intId,
    Some("testAckRef"),
    None,
    None,
    timestamp,
    eligibility = Some(Eligibility(false, false)),
    PAYEStatus.held,
    None,
    None,
    Nil,
    None,
    None,
    Nil,
    lastUpdate,
    partialSubmissionTimestamp = Some(partialSubmissionTimestamp),
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = None
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
    eligibility = Some(Eligibility(false, false)),
    PAYEStatus.submitted,
    None,
    None,
    Nil,
    None,
    None,
    Nil,
    lastUpdate,
    partialSubmissionTimestamp = Some(partialSubmissionTimestamp),
    fullSubmissionTimestamp = Some(fullSubmissionTimestamp),
    acknowledgedTimestamp = None,
    lastAction = None
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

      await(client(s"test-only/feature-flag/desServiceFeature/true").get())
      await(repository.insert(processedSubmission))

      val response = client(s"incorporation-data").post(jsonIncorpStatusUpdate).futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson(crn)

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
             |     "Authorization" : "-",
             |     "transactionName" : "payeRegistrationAdditionalData"
             |  }
             |}
          """.stripMargin).toString(), true, true)
        )
      )

      val reg = await(repository.retrieveRegistration(regId))
      reg shouldBe Some(processedTopUpSubmission.copy(lastUpdate = reg.get.lastUpdate, fullSubmissionTimestamp = reg.get.fullSubmissionTimestamp))

      val regLastUpdate = DateHelper.getDateFromTimestamp(reg.get.lastUpdate)
      val submissionLastUpdate = DateHelper.getDateFromTimestamp(processedSubmission.lastUpdate)

      regLastUpdate.isAfter(submissionLastUpdate) shouldBe true
      reg.get.fullSubmissionTimestamp.nonEmpty shouldBe true
    }

    "return a 200 when Incorporation is rejected" in new Setup {

      setupSimpleAuthMocks()

      stubFor(post(urlMatching("/business-incorporation/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(200)
        )
      )

      await(repository.insert(processedSubmission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"incorporation-data").post(Json.parse(incorpUpdateNoCRN(rejected))).futureValue
      response.status shouldBe 200
      val none: Option[String] = None
      response.json shouldBe Json.toJson(none)

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
             |     "Authorization" : "-",
             |     "transactionName" : "incorporationFailure"
             |   }
             |}
          """.stripMargin).toString(), true, true)
        )
      )

      val reg = await(repository.retrieveRegistration(regId))
      reg shouldBe Some(processedSubmission.copy(status = PAYEStatus.cancelled, lastUpdate = reg.get.lastUpdate))

      val regLastUpdate = DateHelper.getDateFromTimestamp(reg.get.lastUpdate)
      val submissionLastUpdate = DateHelper.getDateFromTimestamp(processedSubmission.lastUpdate)

      regLastUpdate.isAfter(submissionLastUpdate) shouldBe true
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

      await(repository.insert(processedSubmission))

      val response = client(s"incorporation-data").post(jsonIncorpStatusUpdate2).futureValue
      response.status shouldBe 200

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedSubmission)
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

      await(repository.insert(processedTopUpSubmission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"incorporation-data").post(jsonIncorpStatusUpdate).futureValue
      response.status shouldBe 200

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedTopUpSubmission)
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

      await(repository.insert(submission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"incorporation-data").post(jsonIncorpStatusUpdate).futureValue
      response.status shouldBe 500

      await(repository.retrieveRegistration(regId)) shouldBe Some(submission)
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

      await(repository.insert(processedSubmission))
      await(client(s"test-only/feature-flag/desServiceFeature/false").get())

      val response = client(s"incorporation-data").post(jsonIncorpStatusUpdate).futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson(crn)

      val reg = await(repository.retrieveRegistration(regId))
      reg shouldBe Some(processedTopUpSubmission.copy(lastUpdate = reg.get.lastUpdate, fullSubmissionTimestamp = reg.get.fullSubmissionTimestamp))

      val regLastUpdate = DateHelper.getDateFromTimestamp(reg.get.lastUpdate)
      val submissionLastUpdate = DateHelper.getDateFromTimestamp(processedSubmission.lastUpdate)

      regLastUpdate.isAfter(submissionLastUpdate) shouldBe true
      reg.get.fullSubmissionTimestamp.nonEmpty shouldBe true
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

      await(repository.insert(processedSubmission))
      await(client(s"test-only/feature-flag/desServiceFeature/false").get())

      val response = client(s"incorporation-data").post(Json.parse(incorpUpdateNoCRN(rejected))).futureValue
      response.status shouldBe 200
      val none: Option[String] = None
      response.json shouldBe Json.toJson(none)

      val reg = await(repository.retrieveRegistration(regId))
      reg shouldBe Some(processedSubmission.copy(status = PAYEStatus.cancelled, lastUpdate = reg.get.lastUpdate))

      val regLastUpdate = DateHelper.getDateFromTimestamp(reg.get.lastUpdate)
      val submissionLastUpdate = DateHelper.getDateFromTimestamp(processedSubmission.lastUpdate)

      regLastUpdate.isAfter(submissionLastUpdate) shouldBe true
    }
  }

  "updateRegistrationWithEmpRef" should {
    "return an OK with a Json body" when {
      "the emp ref has been updated as APPROVED" in new Setup {
        setupSimpleAuthMocks()

        await(repository.insert(processedSubmission.copy(registrationConfirmation = None, acknowledgementReference = Some("ackRef"))))

        val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "04"))

        val response = client("registration-processed-confirmation?ackref=ackRef").post(testNotification).futureValue
        response.status shouldBe 200
        response.json shouldBe testNotification

        val reg = await(repository.retrieveRegistration(processedSubmission.registrationID)).get
        reg.registrationConfirmation shouldBe Some(
          EmpRefNotification(
            empRef = Some("testEmpRef"),
            timestamp = "2017-01-01T12:00:00Z",
            status = "04"
          )
        )
        reg.status shouldBe PAYEStatus.acknowledged
        reg.acknowledgedTimestamp.isDefined shouldBe true
      }

      "the emp ref has been updated as APPROVED WITH CONDITIONS" in new Setup {
        setupSimpleAuthMocks()

        await(repository.insert(processedSubmission.copy(registrationConfirmation = None, acknowledgementReference = Some("ackRef"))))

        val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "05"))

        val response = client("registration-processed-confirmation?ackref=ackRef").post(testNotification).futureValue
        response.status shouldBe 200
        response.json shouldBe testNotification

        val reg = await(repository.retrieveRegistration(processedSubmission.registrationID)).get
        reg.registrationConfirmation shouldBe Some(
          EmpRefNotification(
            empRef = Some("testEmpRef"),
            timestamp = "2017-01-01T12:00:00Z",
            status = "05"
          )
        )
        reg.status shouldBe PAYEStatus.acknowledged
        reg.acknowledgedTimestamp.isDefined shouldBe true
      }

      "the emp ref has been updated as REJECTED" in new Setup {
        setupSimpleAuthMocks()

        await(repository.insert(processedSubmission.copy(registrationConfirmation = None, acknowledgementReference = Some("ackRef"))))

        val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "06"))

        val response = client("registration-processed-confirmation?ackref=ackRef").post(testNotification).futureValue
        response.status shouldBe 200
        response.json shouldBe testNotification

        val reg = await(repository.retrieveRegistration(processedSubmission.registrationID)).get
        reg.registrationConfirmation shouldBe Some(
          EmpRefNotification(
            empRef = Some("testEmpRef"),
            timestamp = "2017-01-01T12:00:00Z",
            status = "06"
          )
        )
        reg.status shouldBe PAYEStatus.rejected
        reg.acknowledgedTimestamp.isDefined shouldBe true
      }

      "the emp ref has been updated as REJECTED_UNDER_REVIEW_APPREAL" in new Setup {
        setupSimpleAuthMocks()

        await(repository.insert(processedSubmission.copy(registrationConfirmation = None, acknowledgementReference = Some("ackRef"))))

        val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "07"))

        val response = client("registration-processed-confirmation?ackref=ackRef").post(testNotification).futureValue
        response.status shouldBe 200
        response.json shouldBe testNotification

        val reg = await(repository.retrieveRegistration(processedSubmission.registrationID)).get
        reg.registrationConfirmation shouldBe Some(
          EmpRefNotification(
            empRef = Some("testEmpRef"),
            timestamp = "2017-01-01T12:00:00Z",
            status = "07"
          )
        )
        reg.status shouldBe PAYEStatus.rejected
        reg.acknowledgedTimestamp.isDefined shouldBe true
      }
    }

    "the emp ref has been updated as REVOKED" in new Setup {
      setupSimpleAuthMocks()

      await(repository.insert(processedSubmission.copy(registrationConfirmation = None, acknowledgementReference = Some("ackRef"))))

      val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "08"))

      val response = client("registration-processed-confirmation?ackref=ackRef").post(testNotification).futureValue
      response.status shouldBe 200
      response.json shouldBe testNotification

      val reg = await(repository.retrieveRegistration(processedSubmission.registrationID)).get
      reg.registrationConfirmation shouldBe Some(
        EmpRefNotification(
          empRef = Some("testEmpRef"),
          timestamp = "2017-01-01T12:00:00Z",
          status = "08"
        )
      )
      reg.status shouldBe PAYEStatus.rejected
      reg.acknowledgedTimestamp.isDefined shouldBe true
    }

    "the emp ref has been updated as REVOKED_UNDER_REVIEW_APPREAL" in new Setup {
      setupSimpleAuthMocks()

      await(repository.insert(processedSubmission.copy(registrationConfirmation = None, acknowledgementReference = Some("ackRef"))))

      val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "09"))

      val response = client("registration-processed-confirmation?ackref=ackRef").post(testNotification).futureValue
      response.status shouldBe 200
      response.json shouldBe testNotification

      val reg = await(repository.retrieveRegistration(processedSubmission.registrationID)).get
      reg.registrationConfirmation shouldBe Some(
        EmpRefNotification(
          empRef = Some("testEmpRef"),
          timestamp = "2017-01-01T12:00:00Z",
          status = "09"
        )
      )
      reg.status shouldBe PAYEStatus.rejected
      reg.acknowledgedTimestamp.isDefined shouldBe true
    }

    "the emp ref has been updated as DEREGISTERED" in new Setup {
      setupSimpleAuthMocks()

      await(repository.insert(processedSubmission.copy(registrationConfirmation = None, acknowledgementReference = Some("ackRef"))))

      val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "10"))

      val response = client("registration-processed-confirmation?ackref=ackRef").post(testNotification).futureValue
      response.status shouldBe 200
      response.json shouldBe testNotification

      val reg = await(repository.retrieveRegistration(processedSubmission.registrationID)).get
      reg.registrationConfirmation shouldBe Some(
        EmpRefNotification(
          empRef = Some("testEmpRef"),
          timestamp = "2017-01-01T12:00:00Z",
          status = "10"
        )
      )
      reg.status shouldBe PAYEStatus.rejected
      reg.acknowledgedTimestamp.isDefined shouldBe true
    }

    "return a not found" when {
      "a matching reg doc cannot be found" in new Setup {
        setupSimpleAuthMocks()

        val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "04"))

        val response = client("registration-processed-confirmation?ackref=invalidackref").post(testNotification).futureValue
        response.status shouldBe 404

        await(repository.retrieveRegistrationByAckRef("invalidackref")) shouldBe None
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

      val testNotification = EmpRefNotification(Some(encrypt("testEmpRef")), "2017-01-01T12:00:00Z", "04")

      await(repository.insert(submission.copy(status = PAYEStatus.acknowledged, registrationConfirmation = Some(testNotification), acknowledgedTimestamp = Some(acknowledgedTimestamp))))

      val response = client(s"$regId/status").get().futureValue
      response.status shouldBe 200
      response.json shouldBe json
    }

    "return an OK with a partial document status with cancelURL when status is draft, lastUpdate returns formCreationTimestamp" in new Setup {
      val json = Json.parse(s"""{
                               |   "status": "draft",
                               |   "lastUpdate": "$timestamp",
                               |   "cancelURL": "testCancelURL/$regId/del"
                               |}""".stripMargin)

      setupSimpleAuthMocks()

      await(repository.insert(submission.copy(acknowledgementReference = None)))

      val response = client(s"$regId/status").get().futureValue
      response.status shouldBe 200
      response.json shouldBe json
    }

    "return an OK with a partial document status with cancelURL when status is invalid, lastUpdate returns formCreationTimestamp" in new Setup {
      val json = Json.parse(s"""{
                               |   "status": "invalid",
                               |   "lastUpdate": "$timestamp",
                               |   "cancelURL": "testCancelURL/$regId/del"
                               |}""".stripMargin)

      setupSimpleAuthMocks()

      await(repository.insert(submission.copy(acknowledgementReference = None, status = PAYEStatus.invalid)))

      val response = client(s"$regId/status").get().futureValue
      response.status shouldBe 200
      response.json shouldBe json
    }

    "return an OK with a partial document status when status is cancelled, lastUpdate returns formCreationTimestamp" in new Setup {
      val json = Json.parse(s"""{
                               |   "status": "cancelled",
                               |   "lastUpdate": "$lastUpdate"
                               |}""".stripMargin)

      setupSimpleAuthMocks()

      await(repository.insert(submission.copy(acknowledgementReference = None, status = PAYEStatus.cancelled)))

      val response = client(s"$regId/status").get().futureValue
      response.status shouldBe 200
      response.json shouldBe json
    }

    "return an OK with a partial document status when status is held, lastUpdate returns partialSubmissionTimestamp" in new Setup {
      val json = Json.parse(s"""{
                              |   "status": "held",
                              |   "lastUpdate": "$partialSubmissionTimestamp",
                              |   "ackRef": "testAckRef"
                              |}""".stripMargin)

      setupSimpleAuthMocks()

      await(repository.insert(processedSubmission))

      val response = client(s"$regId/status").get().futureValue
      response.status shouldBe 200
      response.json shouldBe json
    }

    "return an OK with a partial document status when status is submitted, lastUpdate returns fullSubmissionTimestamp" in new Setup {
      val json = Json.parse(s"""{
                              |   "status": "submitted",
                              |   "lastUpdate": "$fullSubmissionTimestamp",
                              |   "ackRef": "testAckRef"
                              |}""".stripMargin)

      setupSimpleAuthMocks()

      await(repository.insert(processedTopUpSubmission))

      val response = client(s"$regId/status").get().futureValue
      response.status shouldBe 200
      response.json shouldBe json
    }

    "return an OK with a partial document status with restartURL when status is rejected, lastUpdate returns acknowledgedTimestamp" in new Setup {
      val json = Json.parse(s"""{
                               |   "status": "rejected",
                               |   "lastUpdate": "$acknowledgedTimestamp",
                               |   "ackRef": "testAckRef",
                               |   "restartURL": "testRestartURL"
                               |}""".stripMargin)

      setupSimpleAuthMocks()

      await(repository.insert(submission.copy(status = PAYEStatus.rejected, acknowledgedTimestamp = Some(acknowledgedTimestamp))))

      val response = client(s"$regId/status").get().futureValue
      response.status shouldBe 200
      response.json shouldBe json
    }

    "return a 404 error when the registration Id is invalid" in new Setup {
      setupSimpleAuthMocks()

      val response = client(s"invalid234/status").get().futureValue
      response.status shouldBe 404
    }
  }

  "deletePAYERegistration" should {
    "return an OK after deleting the document" in new Setup {
      setupSimpleAuthMocks()

      await(repository.insert(submission.copy(status = PAYEStatus.rejected, acknowledgedTimestamp = Some(acknowledgedTimestamp))))

      val response = client(s"$regId/delete").delete().futureValue
      response.status shouldBe 200

      await(repository.retrieveRegistration(submission.registrationID)) shouldBe None
    }

    "return a PreconditionFailed response if the document status is not 'rejected'" in new Setup {
      setupSimpleAuthMocks()

      await(repository.insert(submission))

      val response = client(s"$regId/delete").delete().futureValue
      response.status shouldBe 412

      await(repository.retrieveRegistration(submission.registrationID)) shouldBe Some(submission)
    }

    "return a NotFound trying deleting a non existing document" in new Setup {
      setupSimpleAuthMocks()

      await(repository.insert(submission.copy(status = PAYEStatus.rejected, acknowledgedTimestamp = Some(acknowledgedTimestamp))))

      val response = client(s"invalidRegId/delete").delete().futureValue
      response.status shouldBe 404
    }
  }
}
