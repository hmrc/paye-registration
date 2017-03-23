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
import models.incorporation.TopUp
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
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
    "microservice.services.des-service.port" -> s"$mockPort",
    "microservice.services.des-service.url" -> s"$mockHost",
    "application.router" -> "testOnlyDoNotUseInAppConf.Routes"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build

  private def client(path: String) = WS.url(s"http://localhost:$port/paye-registration/$path").withFollowRedirects(false)

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
  val intId = "Int-xxx"
  val timestamp = "2017-01-01T00:00:00"

  val submission = PAYERegistration(
    regId,
    intId,
    Some("testAckRef"),
    timestamp,
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
    intId,
    Some("testAckRef"),
    timestamp,
    PAYEStatus.held,
    None,
    None,
    Nil,
    None,
    None,
    Nil
  )

  val processedTopUpSubmission = PAYERegistration(
    regId,
    intId,
    Some("testAckRef"),
    timestamp,
    PAYEStatus.submitted,
    None,
    None,
    Nil,
    None,
    None,
    Nil
  )

  val incorporationData = TopUp(registrationId = regId, crn = "123456")

  "submit-registration" should {
    "return a 200 with an ack ref" in new Setup {


      setupSimpleAuthMocks()

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(200)
        )
      )

      await(repository.insert(submission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"$regId/submit-registration").put("").futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson("testAckRef")

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedSubmission)
    }

    "return a 500 status when registration is already submitted" in new Setup {
      setupSimpleAuthMocks()

      await(repository.insert(processedSubmission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"$regId/submit-registration").put("").futureValue
      response.status shouldBe 500

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedSubmission)
    }
  }

  "incorporation-data" should {
    "return a 200 with a CRN" in new Setup {


      setupSimpleAuthMocks()

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(200)
        )
      )

      await(client(s"test-only/feature-flag/desServiceFeature/true").get())
      await(repository.insert(processedSubmission))

      val response = client(s"incorporation-data").post(Json.toJson(incorporationData)).futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson("123456")

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedTopUpSubmission)
    }

    "return a 500 status when registration is already submitted" in new Setup {
      setupSimpleAuthMocks()

      await(repository.insert(processedTopUpSubmission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"incorporation-data").post(Json.toJson(incorporationData)).futureValue
      response.status shouldBe 500

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedTopUpSubmission)
    }

    "return a 500 status when registration is not yet submitted with partial" in new Setup {
      setupSimpleAuthMocks()

      await(repository.insert(submission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"incorporation-data").post(Json.toJson(incorporationData)).futureValue
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

      val response = client(s"incorporation-data").post(Json.toJson(incorporationData)).futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson(incorporationData.crn)

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedTopUpSubmission)
    }
  }

}

