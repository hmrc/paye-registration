/*
 * Copyright 2020 HM Revenue & Customs
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

import auth.CryptoSCRS
import com.kenshoo.play.metrics.Metrics
import enums.PAYEStatus
import helpers.DateHelper
import itutil.{IntegrationSpecBase, WiremockHelper}
import models.{PAYERegistration, SICCode}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.{Application, Configuration}
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.RegistrationMongo

import scala.concurrent.ExecutionContext.Implicits.global

class SICCodesISpec extends IntegrationSpecBase {
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
    lazy val mockcryptoSCRS = app.injector.instanceOf[CryptoSCRS]
    val mongo = new RegistrationMongo(mockMetrics, mockDateHelper, reactiveMongoComponent, sConfig, mockcryptoSCRS)
    val repository = mongo.store

    def insertToDb(data: PAYERegistration) = await(repository.insert(data))

    await(repository.drop)
    await(repository.ensureIndexes)
  }

  "PAYE Registration API - SIC Codes" should {
    val lastUpdate = "2017-05-09T07:58:35Z"

    val validSICCodes = Seq(
      SICCode(code = Some("123"), description = Some("consulting")),
      SICCode(code = None, description = Some("something"))
    )

    "Return a 200 when the user gets sic codes" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val transactionID = "NN1234"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"
      insertToDb(
        PAYERegistration(
          regID,
          transactionID,
          intID,
          Some("testAckRef"),
          None,
          None,
          timestamp,
          PAYEStatus.draft,
          None,
          None,
          Seq.empty,
          None,
          validSICCodes,
          lastUpdate,
          partialSubmissionTimestamp = None,
          fullSubmissionTimestamp = None,
          acknowledgedTimestamp = None,
          lastAction = None,
          employmentInfo = None
        )
      )

      val response = client(s"/${regID}/sic-codes").get.futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson(validSICCodes)
    }

    "Return a 200 when the user upserts sic codes" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val transactionID = "NN1234"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"
      insertToDb(
        PAYERegistration(
          regID,
          transactionID,
          intID,
          Some("testAckRef"),
          None,
          None,
          timestamp,
          PAYEStatus.draft,
          None,
          None,
          Seq.empty,
          None,
          Seq.empty,
          lastUpdate,
          partialSubmissionTimestamp = None,
          fullSubmissionTimestamp = None,
          acknowledgedTimestamp = None,
          lastAction = None,
          employmentInfo = None
        )
      )

      val getResponse1 = client(s"/${regID}/sic-codes").get.futureValue
      getResponse1.status shouldBe 404

      val patchResponse = client(s"/${regID}/sic-codes")
        .patch[JsValue](Json.toJson(validSICCodes))
        .futureValue
      patchResponse.status shouldBe 200

      val getResponse2 = client(s"/${regID}/sic-codes").get.futureValue
      getResponse2.status shouldBe 200
      getResponse2.json shouldBe Json.toJson(validSICCodes)
    }

    "Return a 403 when the user is not authorised to get sic codes" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val transactionID = "NN1234"
      val intID = "Int-xxx-yyy-zzz"
      val timestamp = "2017-01-01T00:00:00"
      insertToDb(
        PAYERegistration(
          regID,
          transactionID,
          intID,
          Some("testAckRef"),
          None,
          None,
          timestamp,
          PAYEStatus.draft,
          None,
          None,
          Seq.empty,
          None,
          Seq.empty,
          lastUpdate,
          partialSubmissionTimestamp = None,
          fullSubmissionTimestamp = None,
          acknowledgedTimestamp = None,
          lastAction = None,
          employmentInfo = None
        )
      )

      val response = client(s"/${regID}/sic-codes").get.futureValue
      response.status shouldBe 403
    }

    "Return a 403 when the user is not authorised to upsert sic codes" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val transactionID = "NN1234"
      val intID = "Int-abc-yyy-zzz"
      val timestamp = "2017-01-01T00:00:00"
      insertToDb(
        PAYERegistration(
          regID,
          transactionID,
          intID,
          Some("testAckRef"),
          None,
          None,
          timestamp,
          PAYEStatus.draft,
          None,
          None,
          Seq.empty,
          None,
          Seq.empty,
          lastUpdate,
          partialSubmissionTimestamp = None,
          fullSubmissionTimestamp = None,
          acknowledgedTimestamp = None,
          lastAction = None,
          employmentInfo = None
        )
      )

      val response = client(s"/${regID}/sic-codes")
        .patch(Json.toJson(validSICCodes))
        .futureValue
      response.status shouldBe 403
    }

    "Return a 404 if the registration is missing" in new Setup {
      setupSimpleAuthMocks()

      val response = client(s"/12345").get.futureValue
      response.status shouldBe 404
    }
  }
}