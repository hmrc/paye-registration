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
package api

import java.time.{LocalDate, ZoneOffset, ZonedDateTime}

import com.kenshoo.play.metrics.Metrics
import enums.PAYEStatus
import helpers.DateHelper
import itutil.{IntegrationSpecBase, WiremockHelper}
import models.validation.APIValidation
import models.{Eligibility, Employment, PAYERegistration}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.{Application, Configuration}
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.RegistrationMongo

import scala.concurrent.ExecutionContext.Implicits.global

class EmploymentISpec extends IntegrationSpecBase {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.auth.host" -> s"$mockHost",
    "microservice.services.auth.port" -> s"$mockPort"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build

  lazy val reactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]
  lazy val sConfig = app.injector.instanceOf[Configuration]

  private def client(path: String) = ws.url(s"http://localhost:$port/paye-registration$path").withFollowRedirects(false)
  class Setup {
    lazy val mockMetrics = app.injector.instanceOf[Metrics]
    lazy val mockDateHelper = app.injector.instanceOf[DateHelper]
    val mongo = new RegistrationMongo(mockMetrics, mockDateHelper, reactiveMongoComponent, sConfig)
    val repository = mongo.store

    def insertToDb(paye: PAYERegistration) = {
      await(repository.insert(paye))
      await(repository.count) shouldBe 1
    }

    def upsertToDb(paye: PAYERegistration) = await(repository.updateRegistration(paye))

    await(repository.drop)
    await(repository.ensureIndexes)
  }


  "PAYE Registration API - Employment" should {
    val lastUpdate = "2017-05-09T07:58:35Z"
    val dt = ZonedDateTime.of(2000,1,20,16,1,0,0,ZoneOffset.UTC)

    val validEmployment = Employment(
      employees = true,
      companyPension = Some(true),
      subcontractors = true,
      firstPaymentDate = LocalDate.of(1900, 1, 1)
    )

    "Return a 200 when the user gets employment" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val transactionID = "NN1234"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"
      upsertToDb(
        PAYERegistration(
          regID,
          transactionID,
          intID,
          Some("testAckRef"),
          None,
          None,
          timestamp,
          Some(Eligibility(false, false)),
          PAYEStatus.draft,
          None,
          None,
          Seq.empty,
          None,
          Some(validEmployment),
          Seq.empty,
          lastUpdate,
          partialSubmissionTimestamp = None,
          fullSubmissionTimestamp = None,
          acknowledgedTimestamp = None,
          lastAction = Some(dt)
        )
      )

      val response = client(s"/${regID}/employment").get.futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson(validEmployment)(Employment.format(APIValidation))
    }

    "Return a 200 when the user upserts employment" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val transactionID = "NN1234"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"
      upsertToDb(
        PAYERegistration(
          regID,
          transactionID,
          intID,
          Some("testAckRef"),
          None,
          None,
          timestamp,
          Some(Eligibility(false, false)),
          PAYEStatus.draft,
          None,
          None,
          Seq.empty,
          None,
          None,
          Seq.empty,
          lastUpdate,
          partialSubmissionTimestamp = None,
          fullSubmissionTimestamp = None,
          acknowledgedTimestamp = None,
          lastAction = Some(dt)
        )
      )

      val getResponse1 = client(s"/${regID}/employment").get.futureValue
      getResponse1.status shouldBe 404

      val patchResponse = client(s"/${regID}/employment")
        .patch[JsValue](Json.toJson(validEmployment)(Employment.format(APIValidation)))
        .futureValue
      patchResponse.status shouldBe 200

      val getResponse2 = client(s"/${regID}/employment").get.futureValue
      getResponse2.status shouldBe 200
      getResponse2.json shouldBe Json.toJson(validEmployment)(Employment.format(APIValidation))
    }

    "Return a 403 when the user is not authorised to get employment" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val transactionID = "NN1234"
      val intID = "Int-xxx-yyy-zzz"
      val timestamp = "2017-01-01T00:00:00"
      upsertToDb(
        PAYERegistration(
          regID,
          transactionID,
          intID,
          Some("testAckRef"),
          None,
          None,
          timestamp,
          Some(Eligibility(false, false)),
          PAYEStatus.draft,
          None,
          None,
          Seq.empty,
          None,
          None,
          Seq.empty,
          lastUpdate,
          partialSubmissionTimestamp = None,
          fullSubmissionTimestamp = None,
          acknowledgedTimestamp = None,
          lastAction = Some(dt)
        )
      )

      val response = client(s"/${regID}/employment").get.futureValue
      response.status shouldBe 403
    }

    "Return a 403 when the user is not authorised to upsert employment" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val transactionID = "NN1234"
      val intID = "Int-xxx-yyy-zzz"
      val timestamp = "2017-01-01T00:00:00"
      upsertToDb(
        PAYERegistration(
          regID,
          transactionID,
          intID,
          Some("testAckRef"),
          None,
          None,
          timestamp,
          Some(Eligibility(false, false)),
          PAYEStatus.draft,
          None,
          None,
          Seq.empty,
          None,
          None,
          Seq.empty,
          lastUpdate,
          partialSubmissionTimestamp = None,
          fullSubmissionTimestamp = None,
          acknowledgedTimestamp = None,
          lastAction = Some(dt)
        )
      )

      val response = client(s"/${regID}/employment")
        .patch(Json.toJson(validEmployment)(Employment.format(APIValidation)))
        .futureValue
      response.status shouldBe 403
    }

    "Return a 404 if the registration is missing" in new Setup {
      setupSimpleAuthMocks()

      val response = client(s"/12345").get.futureValue
      response.status shouldBe 404
    }

    "Return a 400 when upsert employment with a too early date" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val transactionID = "NN1234"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"
      upsertToDb(
        PAYERegistration(
          regID,
          transactionID,
          intID,
          Some("testAckRef"),
          None,
          None,
          timestamp,
          Some(Eligibility(false, false)),
          PAYEStatus.draft,
          None,
          None,
          Seq.empty,
          None,
          None,
          Seq.empty,
          lastUpdate,
          partialSubmissionTimestamp = None,
          fullSubmissionTimestamp = None,
          acknowledgedTimestamp = None,
          lastAction = Some(dt)
        )
      )

      val getResponse1 = client(s"/${regID}/employment").get.futureValue
      getResponse1.status shouldBe 404

      val wrongEmploymentEarlyDate = Employment(
        employees = true,
        companyPension = Some(true),
        subcontractors = true,
        firstPaymentDate = LocalDate.of(1899, 12, 31)
      )

      val patchResponse = client(s"/${regID}/employment")
        .patch[JsValue](Json.toJson(wrongEmploymentEarlyDate)(Employment.format(APIValidation)))
        .futureValue
      patchResponse.status shouldBe 400

      val getResponse2 = client(s"/${regID}/employment").get.futureValue
      getResponse2.status shouldBe 404
    }
  }
}