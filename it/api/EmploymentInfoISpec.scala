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

package api

import java.time.{LocalDate, ZoneOffset, ZonedDateTime}

import auth.CryptoSCRS
import com.kenshoo.play.metrics.Metrics
import enums.{Employing, PAYEStatus}
import helpers.DateHelper
import itutil.{IntegrationSpecBase, WiremockHelper}
import models.validation.APIValidation
import models.{EmploymentInfo, PAYERegistration}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.{Application, Configuration}
import play.api.test.Helpers._
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.RegistrationMongoRepository
import utils.SystemDate

import scala.concurrent.ExecutionContext.Implicits.global

class EmploymentInfoISpec extends IntegrationSpecBase {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.auth.host" -> s"$mockHost",
    "microservice.services.auth.port" -> s"$mockPort",
    "microservice.services.incorporation-information.host" -> s"$mockHost",
    "microservice.services.incorporation-information.port" -> s"$mockPort"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build

  lazy val reactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]
  lazy val sConfig = app.injector.instanceOf[Configuration]
  lazy val mockcryptoSCRS = app.injector.instanceOf[CryptoSCRS]

  private def client(path: String) = ws.url(s"http://localhost:$port/paye-registration$path").withFollowRedirects(false)
  class Setup {
    lazy val mockMetrics = app.injector.instanceOf[Metrics]
    lazy val mockDateHelper = app.injector.instanceOf[DateHelper]
    val repository = new RegistrationMongoRepository(mockMetrics, mockDateHelper, reactiveMongoComponent, sConfig, mockcryptoSCRS)

    def insertToDb(paye: PAYERegistration) = {
      await(repository.insert(paye))
      await(repository.count) shouldBe 1
    }

    def upsertToDb(paye: PAYERegistration) = await(repository.updateRegistration(paye))

    await(repository.drop)
    await(repository.ensureIndexes)
  }

  "PAYE Registration API - EmploymentInfo" should {
    val lastUpdate = "2017-05-09T07:58:35Z"
    val dt = ZonedDateTime.of(2000,1,20,16,1,0,0,ZoneOffset.UTC)
    val currentDate = SystemDate.getSystemDate.toLocalDate

    val validEmploymentInfo = EmploymentInfo(
      employees = Employing.willEmployNextYear,
      companyPension = None,
      construction = true,
      subcontractors = true,
      firstPaymentDate = LocalDate.of(currentDate.getYear, 4, 6)
    )

    "Return a 200 when the user gets employment-info" in new Setup {
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
          lastAction = Some(dt),
          employmentInfo = Some(validEmploymentInfo)
        )
      )

      val response = client(s"/${regID}/employment-info").get.futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson(validEmploymentInfo)(EmploymentInfo.format(APIValidation))
    }

    "Return a 200 when the user upserts employment-info" in new Setup {
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
          lastAction = Some(dt),
          employmentInfo = None
        )
      )

      stubGet(s"/incorporation-information/$transactionID/incorporation-update", 204, "")

      val getResponse1 = client(s"/${regID}/employment-info").get.futureValue
      getResponse1.status shouldBe 204

      val patchResponse = client(s"/${regID}/employment-info")
        .patch[JsValue](Json.toJson(validEmploymentInfo)(EmploymentInfo.format(APIValidation)))
        .futureValue
      patchResponse.status shouldBe 200

      val getResponse2 = client(s"/${regID}/employment-info").get.futureValue
      getResponse2.status shouldBe 200
      getResponse2.json shouldBe Json.toJson(validEmploymentInfo)(EmploymentInfo.format(APIValidation))
    }

    "Return a 403 when the user is not authorised to get employment-info" in new Setup {
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
          lastAction = Some(dt),
          employmentInfo = None
        )
      )

      val response = client(s"/${regID}/employment-info").get.futureValue
      response.status shouldBe 403
    }

    "Return a 403 when the user is not authorised to upsert employment-info" in new Setup {
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
          lastAction = Some(dt),
          employmentInfo = None
        )
      )

      val response = client(s"/${regID}/employment-info")
        .patch(Json.toJson(validEmploymentInfo)(EmploymentInfo.format(APIValidation)))
        .futureValue
      response.status shouldBe 403
    }

    "Return a 404 if the registration is missing" in new Setup {
      setupSimpleAuthMocks()

      val response = client(s"/12345/employment-info").get.futureValue
      response.status shouldBe 404
    }

    "Return a 400 when upsert employment-info with a wrong next tax year date" in new Setup {
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
          lastAction = Some(dt),
          employmentInfo = None
        )
      )

      stubGet(s"/incorporation-information/$transactionID/incorporation-update", 204, "")

      val getResponse1 = client(s"/${regID}/employment-info").get.futureValue
      getResponse1.status shouldBe 204

      val wrongEmploymentNextYearDate = EmploymentInfo(
        employees = Employing.willEmployNextYear,
        companyPension = Some(true),
        construction = true,
        subcontractors = true,
        firstPaymentDate = LocalDate.of(2018, 4, 5)
      )

      val patchResponse = client(s"/${regID}/employment-info")
        .patch[JsValue](Json.toJson(wrongEmploymentNextYearDate)(EmploymentInfo.format(APIValidation)))
        .futureValue
      patchResponse.status shouldBe 400

      val getResponse2 = client(s"/${regID}/employment-info").get.futureValue
      getResponse2.status shouldBe 204
    }

    s"Return a 400 when upsert employment-info with a wrong payment date when employees is set to ${Employing.alreadyEmploying}" in new Setup {
      val now = SystemDate.getSystemDate.toLocalDate
      val incorpDate = now.minusYears(1)

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
          lastAction = Some(dt),
          employmentInfo = None
        )
      )

      stubGet(s"/incorporation-information/$transactionID/incorporation-update", 200, s"""{"incorporationDate": "$incorpDate"}""")

      val getResponse1 = client(s"/${regID}/employment-info").get.futureValue
      getResponse1.status shouldBe 204

      val wrongEmploymentPaymentDate = EmploymentInfo(
        employees = Employing.alreadyEmploying,
        companyPension = Some(true),
        construction = true,
        subcontractors = true,
        firstPaymentDate = incorpDate.minusDays(1)
      )

      val patchResponse = client(s"/${regID}/employment-info")
        .patch[JsValue](Json.toJson(wrongEmploymentPaymentDate)(EmploymentInfo.format(APIValidation)))
        .futureValue
      patchResponse.status shouldBe 400

      val getResponse2 = client(s"/${regID}/employment-info").get.futureValue
      getResponse2.status shouldBe 204
    }

    s"Return a 400 when upsert employment-info with employees is set to ${Employing.alreadyEmploying} but missing corporation date" in new Setup {
      val now = SystemDate.getSystemDate.toLocalDate

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
          lastAction = Some(dt),
          employmentInfo = None
        )
      )

      stubGet(s"/incorporation-information/$transactionID/incorporation-update", 204, "")

      val getResponse1 = client(s"/${regID}/employment-info").get.futureValue
      getResponse1.status shouldBe 204

      val correctEmploymentPaymentDate = EmploymentInfo(
        employees = Employing.alreadyEmploying,
        companyPension = Some(true),
        construction = true,
        subcontractors = true,
        firstPaymentDate = now.minusDays(1)
      )

      val patchResponse = client(s"/${regID}/employment-info")
        .patch[JsValue](Json.toJson(correctEmploymentPaymentDate)(EmploymentInfo.format(APIValidation)))
        .futureValue
      patchResponse.status shouldBe 400

      val getResponse2 = client(s"/${regID}/employment-info").get.futureValue
      getResponse2.status shouldBe 204
    }

    s"Return a 200 when upsert employment-info with a correct payment date when employees is set to ${Employing.alreadyEmploying}" in new Setup {
      val now = SystemDate.getSystemDate.toLocalDate
      val incorpDate = now.minusYears(1)

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
          lastAction = Some(dt),
          employmentInfo = None
        )
      )

      stubGet(s"/incorporation-information/$transactionID/incorporation-update", 200, s"""{"incorporationDate": "$incorpDate"}""")

      val getResponse1 = client(s"/${regID}/employment-info").get.futureValue
      getResponse1.status shouldBe 204

      val correctEmploymentPaymentDate = EmploymentInfo(
        employees = Employing.alreadyEmploying,
        companyPension = Some(true),
        construction = true,
        subcontractors = true,
        firstPaymentDate = incorpDate.plusDays(1)
      )

      val patchResponse = client(s"/${regID}/employment-info")
        .patch[JsValue](Json.toJson(correctEmploymentPaymentDate)(EmploymentInfo.format(APIValidation)))
        .futureValue
      patchResponse.status shouldBe 200

      val getResponse2 = client(s"/${regID}/employment-info").get.futureValue
      getResponse2.status shouldBe 200
      getResponse2.json shouldBe Json.toJson(correctEmploymentPaymentDate)(EmploymentInfo.format(APIValidation))
    }
  }
}
