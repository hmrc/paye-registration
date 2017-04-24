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
import itutil.{IntegrationSpecBase, WiremockHelper}
import models._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WS
import play.api.{Application, Play}
import repositories.{RegistrationMongo, RegistrationMongoRepository, SequenceMongo, SequenceMongoRepository}
import services.MetricsService

import scala.concurrent.ExecutionContext.Implicits.global

class RegistrationControllerISpec extends IntegrationSpecBase {

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
    "microservice.services.des-service.url" -> s"$mockHost",
    "microservice.services.des-service.port" -> s"$mockPort",
    "application.router" -> "testOnlyDoNotUseInAppConf.Routes",
    "microservice.services.incorporation-information.host" -> s"$mockHost",
    "microservice.services.incorporation-information.port" -> s"$mockPort"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  private def client(path: String) = WS.url(s"http://localhost:$port/paye-registration/$path").withFollowRedirects(false)

  private val regime = "paye"
  private val subscriber = "SCRS"

  class Setup {
    lazy val mockMetrics = Play.current.injector.instanceOf[MetricsService]
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

  val submission = PAYERegistration(
    regId,
    transactionID,
    intId,
    Some("testAckRef"),
    None,
    Some(EmpRefNotification(
      Some("testEmpRef"),
      "2017-01-01T12:00:00Z",
      "testStatus"
    )),
    timestamp,
    Some(Eligibility(false, false)),
    PAYEStatus.draft,
    Some("Director"),
    Some(
      CompanyDetails(
        "testCompanyName",
        Some("test"),
        Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), Some("UK")),
        Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), Some("UK")),
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
        correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK"))
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
      SICCode(code = Some("123"), description = Some("consulting")),
      SICCode(code = None, description = Some("something"))
    )
  )

  val processedSubmission = PAYERegistration(
    regId,
    transactionID,
    intId,
    Some("testAckRef"),
    None,
    Some(EmpRefNotification(
      Some("testEmpRef"),
      "2017-01-01T12:00:00Z",
      "testStatus"
    )),
    timestamp,
    eligibility = Some(Eligibility(false, false)),
    PAYEStatus.held,
    None,
    None,
    Nil,
    None,
    None,
    Nil
  )

  val rejectedSubmission = submission.copy(status = PAYEStatus.cancelled)

  val processedTopUpSubmission = PAYERegistration(
    regId,
    transactionID,
    intId,
    Some("testAckRef"),
    None,
    Some(EmpRefNotification(
      Some("testEmpRef"),
      "2017-01-01T12:00:00Z",
      "testStatus"
    )),
    timestamp,
    eligibility = Some(Eligibility(false, false)),
    PAYEStatus.submitted,
    None,
    None,
    Nil,
    None,
    None,
    Nil
  )

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
       |      "timestamp" : "2017-12-21T10:13:09.429Z"
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
       |      "timestamp" : "2017-12-21T10:13:09.429Z"
       |    }
       |  }
       |}
        """.stripMargin
  }
  val jsonIncorpStatusUpdate = Json.parse(incorpUpdate(accepted))

  "submit-registration" should {
    "return a 200 with an ack ref when a partial DES submission completes successfully" in new Setup {
      setupSimpleAuthMocks()

      val regime = "paye"
      val subscriber = "SCRS"

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(200)
        )
      )

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      await(repository.insert(submission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"$regId/submit-registration").put("").futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson("testAckRef")

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedSubmission)
    }

    "return a 200 with an ack ref when a full DES submission completes successfully" in new Setup {
      setupSimpleAuthMocks()

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(200)
        )
      )

      stubFor(post(urlMatching(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              incorpUpdate(accepted)
            )
        )
      )

      await(repository.insert(submission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"$regId/submit-registration").put("").futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson("testAckRef")

      await(repository.retrieveRegistration(regId)).get.status shouldBe PAYEStatus.submitted
    }

    "return a 200 status with an ackRef when DES returns a 409" in new Setup {
      setupSimpleAuthMocks()

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody("""{"acknowledgement_reference" : "testAckRef"}""")
        )
      )

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      await(repository.insert(submission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"$regId/submit-registration").put("").futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson("testAckRef")

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedSubmission)
    }

    "return a 204 status when Incorporation was rejected at PAYE Submission" in new Setup {
      setupSimpleAuthMocks()

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 200, incorpUpdate(rejected))

      await(repository.insert(submission))

      val response = client(s"$regId/submit-registration").put("").futureValue
      response.status shouldBe 204

      await(repository.retrieveRegistration(regId)) shouldBe Some(rejectedSubmission)
    }

    "return a 502 status when DES returns a 499" in new Setup {
      setupSimpleAuthMocks()

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(499)
        )
      )

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      await(repository.insert(submission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"$regId/submit-registration").put("").futureValue
      response.status shouldBe 502

      await(repository.retrieveRegistration(regId)) shouldBe Some(submission)
    }

    "return a 502 status when DES returns a 5xx" in new Setup {
      setupSimpleAuthMocks()

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(533)
        )
      )

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      await(repository.insert(submission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"$regId/submit-registration").put("").futureValue
      response.status shouldBe 502

      await(repository.retrieveRegistration(regId)) shouldBe Some(submission)
    }

    "return a 400 status when DES returns a 4xx" in new Setup {
      setupSimpleAuthMocks()

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(433)
        )
      )

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      await(repository.insert(submission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"$regId/submit-registration").put("").futureValue
      response.status shouldBe 400

      await(repository.retrieveRegistration(regId)) shouldBe Some(submission)
    }

    "return a 500 status when registration has already been cleared post-submission in mongo" in new Setup {
      setupSimpleAuthMocks()

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      await(repository.insert(processedSubmission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"$regId/submit-registration").put("").futureValue
      response.status shouldBe 500

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedSubmission)
    }
  }

  "incorporation-data" should {
    "return a 200 with a crn" in new Setup {
      setupSimpleAuthMocks()

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
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

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedTopUpSubmission)
    }

    "return a 404 status when registration is not found" in new Setup {
      setupSimpleAuthMocks()

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
           |      "timestamp" : "2017-12-21T10:13:09.429Z"
           |  }
           |}
        """.stripMargin)

      await(repository.insert(processedSubmission))

      val response = client(s"incorporation-data").post(jsonIncorpStatusUpdate2).futureValue
      response.status shouldBe 404

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedSubmission)
    }

    "return a 500 status when registration is already submitted" in new Setup {
      setupSimpleAuthMocks()

      await(repository.insert(processedTopUpSubmission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"incorporation-data").post(jsonIncorpStatusUpdate).futureValue
      response.status shouldBe 500

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedTopUpSubmission)
    }

    "return a 500 status when registration is not yet submitted with partial" in new Setup {
      setupSimpleAuthMocks()

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

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedTopUpSubmission)
    }

    "return a 200 when Incorporation is rejected" in new Setup {

      setupSimpleAuthMocks()

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

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedSubmission.copy(status = PAYEStatus.cancelled))
    }
  }

  "updateRegistrationWithEmpRef" should {
    "return an OK with a Json body" when {
      "the emp ref has been updated" in new Setup {
        setupSimpleAuthMocks()

        await(repository.insert(processedSubmission.copy(registrationConfirmation = None)))

        val testNotification = Json.toJson(EmpRefNotification(Some("testEmpRef"), "2017-01-01T12:00:00Z", "04"))

        val response = client("registration-processed-confirmation?ackref=testAckRef").post(testNotification).futureValue
        response.status shouldBe 200
        response.json shouldBe testNotification
      }
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
    "return an OK with the correct statuses" in new Setup {
      await(repository.drop)
      await(repository.ensureIndexes)

      setupSimpleAuthMocks()

      def newSubmission(id: String, status: PAYEStatus.Value) = {
        submission.copy(registrationID = id, transactionID = id, status = status)
      }

      await(repository.insert(newSubmission("11111", PAYEStatus.draft)))
      await(repository.insert(newSubmission("22222", PAYEStatus.held)))
      await(repository.insert(newSubmission("33333", PAYEStatus.submitted)))
      await(repository.insert(newSubmission("44444", PAYEStatus.acknowledged)))
      await(repository.insert(newSubmission("55555", PAYEStatus.invalid)))
      await(repository.insert(newSubmission("66666", PAYEStatus.cancelled)))
      await(repository.insert(newSubmission("77777", PAYEStatus.rejected)))

      def getStatus(id: String) = client(s"$id/status").get().futureValue

      val draft = getStatus("11111")
      val held = getStatus("22222")
      val submitted = getStatus("33333")
      val acknowledged = getStatus("44444")
      val invalid = getStatus("55555")
      val cancelled = getStatus("66666")
      val rejected = getStatus("77777")
      val missing = getStatus("88888")

      def status(s: String) = Json.parse(s"""{"status":"$s"}""").as[JsObject]

      draft.status shouldBe 200
      draft.json shouldBe status("draft")

      held.status shouldBe 200
      held.json shouldBe status("held")

      submitted.status shouldBe 200
      submitted.json shouldBe status("submitted")

      acknowledged.status shouldBe 200
      acknowledged.json shouldBe status("acknowledged")

      invalid.status shouldBe 200
      invalid.json shouldBe status("invalid")

      cancelled.status shouldBe 200
      cancelled.json shouldBe status("cancelled")

      rejected.status shouldBe 200
      rejected.json shouldBe status("rejected")

      missing.status shouldBe 404
    }
  }
}
