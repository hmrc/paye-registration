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

package api

import auth.CryptoSCRS
import com.kenshoo.play.metrics.Metrics
import enums.PAYEStatus
import helpers.DateHelper
import itutil.{IntegrationSpecBase, WiremockHelper}
import models.PAYERegistration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import repositories.RegistrationMongoRepository
import uk.gov.hmrc.mongo.MongoComponent

import java.time.{ZoneOffset, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global

class PayeRegistrationISpec extends IntegrationSpecBase {

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

  lazy val mongoComponent = app.injector.instanceOf[MongoComponent]
  lazy val sConfig = app.injector.instanceOf[Configuration]
  lazy val mockcryptoSCRS = app.injector.instanceOf[CryptoSCRS]

  class Setup {
    lazy val mockMetrics = app.injector.instanceOf[Metrics]
    lazy val mockDateHelper = app.injector.instanceOf[DateHelper]
    val repository = new RegistrationMongoRepository(mockMetrics, mockDateHelper, mongoComponent, sConfig, mockcryptoSCRS)

    await(repository.dropCollection)
  }


  "PAYE Registration API - PAYE Registration Document" should {
    val lastUpdate = "2017-05-09T07:58:35Z"

    "Return a 200 for a minimal registration" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val transactionID = "NN1234"
      val intID = "Int-xxx"
      val ackRef = "BRPY-xxx"
      val timestamp = "2017-01-01T00:00:00"
      val dt = ZonedDateTime.of(2000,1,20,16,1,0,0,ZoneOffset.UTC)
      val dtTimestamp = "2000-01-20T16:01:00Z"

      await(repository.updateRegistration(
        PAYERegistration(
          regID,
          transactionID,
          intID,
          Some(ackRef),
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
      ))

      val response = client(s"/${regID}").get.futureValue

      response.status mustBe 200
      response.json mustBe Json.obj(
        "registrationID" -> regID,
        "transactionID" -> transactionID,
        "internalID" -> intID,
        "acknowledgementReference" -> ackRef,
        "formCreationTimestamp" -> timestamp,
        "status" -> PAYEStatus.draft,
        "directors" -> Json.arr(),
        "sicCodes" -> Json.arr(),
        "lastUpdate" -> lastUpdate,
        "lastAction" -> dtTimestamp
      )
    }

    "Return a 403 when the user is not authorised to get a paye registration" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val transactionID = "NN1234"
      val intID = "Int-xxx-yyy-zzz"
      val timestamp = "2017-01-01T00:00:00"
      val dt = ZonedDateTime.of(2000,1,20,16,1,0,0,ZoneOffset.UTC)
      await(repository.updateRegistration(
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
      ))

      val response = client(s"/${regID}").get.futureValue
      response.status mustBe 403
    }
  }
}