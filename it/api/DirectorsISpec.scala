/*
 * Copyright 2022 HM Revenue & Customs
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
import models._
import models.validation.APIValidation
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.RegistrationMongoRepository

import java.time.{ZoneOffset, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global


class DirectorsISpec extends IntegrationSpecBase {
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
  lazy val mockcryptoSCRS = app.injector.instanceOf[CryptoSCRS]

  private def client(path: String) = ws.url(s"http://localhost:$port/paye-registration$path").withFollowRedirects(false)

  class Setup {
    lazy val mockMetrics = app.injector.instanceOf[Metrics]
    lazy val mockDateHelper = app.injector.instanceOf[DateHelper]
    val repository = new RegistrationMongoRepository(mockMetrics, mockDateHelper, reactiveMongoComponent, sConfig, mockcryptoSCRS)

    def upsertToDb(paye: PAYERegistration) = await(repository.updateRegistration(paye))

    await(repository.drop)
    await(repository.ensureIndexes)
  }


  "PAYE Registration API - Directors" should {
    val lastUpdate = "2017-05-09T07:58:35Z"
    val dt = ZonedDateTime.of(2000,1,20,16,1,0,0,ZoneOffset.UTC)

    val validDirectors = Seq(
      Director(
        Name(
          forename = Some("Thierry"),
          otherForenames = Some("Dominique"),
          surname = Some("Henry"),
          title = Some("Sir")
        ),
        Some("SR123456C")
      ),
      Director(
        Name(
          forename = Some("David"),
          otherForenames = Some("Jesus"),
          surname = Some("Trezeguet"),
          title = Some("Mr")
        ),
        Some("SR000009C")
      )
    )

    "Return a 200 when the user gets directors" in new Setup {
      setupSimpleAuthMocks()
      val regID = "12345"
      val txID = "NN1234"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"

      val mongoFormattedRegDoc =
        PAYERegistration(
          registrationID = regID,
          transactionID = txID,
          internalID = intID,
          acknowledgementReference = Some("testAckRef"),
          crn = None,
          registrationConfirmation = None,
          formCreationTimestamp = timestamp,
          status = PAYEStatus.draft,
          completionCapacity = None,
          companyDetails = None,
          directors = validDirectors,
          payeContact = None,
          sicCodes = Seq.empty,
          lastUpdate = lastUpdate,
          partialSubmissionTimestamp = None,
          fullSubmissionTimestamp = None,
          acknowledgedTimestamp = None,
          lastAction = Some(dt),
          employmentInfo = None
      )
      upsertToDb(mongoFormattedRegDoc)


      val response = client(s"/${regID}/directors").get.futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson(validDirectors)(Director.directorSequenceWriter(APIValidation))
    }

    "Return a 200 when the user upserts directors" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val transactionID = "NN1234"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"
      upsertToDb(
        PAYERegistration(
          registrationID = regID,
          transactionID = transactionID,
          internalID = intID,
          acknowledgementReference = Some("testAckRef"),
          crn = None,
          registrationConfirmation = None,
          formCreationTimestamp = timestamp,
          status = PAYEStatus.draft,
          completionCapacity = None,
          companyDetails = None,
          directors = Seq.empty,
          payeContact = None,
          sicCodes = Seq.empty,
          lastUpdate = lastUpdate,
          partialSubmissionTimestamp = None,
          fullSubmissionTimestamp = None,
          acknowledgedTimestamp = None,
          lastAction = Some(dt),
          employmentInfo = None
        )
      )

      val getResponse1 = client(s"/${regID}/directors").get.futureValue
      getResponse1.status shouldBe 404

      val patchResponse = client(s"/${regID}/directors")
        .patch[JsValue](Json.toJson(validDirectors)(Director.directorSequenceWriter(APIValidation)))
        .futureValue
      patchResponse.status shouldBe 200

      val getResponse2 = client(s"/${regID}/directors").get.futureValue
      getResponse2.status shouldBe 200
      getResponse2.json shouldBe Json.toJson(validDirectors)(Director.directorSequenceWriter(APIValidation))
    }

    "Return a 403 when the user is not authorised to get directors" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val transactionID = "NN1234"
      val intID = "Int-xxx-yyy-zzz"
      val timestamp = "2017-01-01T00:00:00"
      upsertToDb(
        PAYERegistration(
          registrationID = regID,
          transactionID = transactionID,
          internalID = intID,
          acknowledgementReference = Some("testAckRef"),
          crn = None,
          registrationConfirmation = None,
          formCreationTimestamp = timestamp,
          status = PAYEStatus.draft,
          completionCapacity = None,
          companyDetails = None,
          directors = Seq.empty,
          payeContact = None,
          sicCodes = Seq.empty,
          lastUpdate = lastUpdate,
          partialSubmissionTimestamp = None,
          fullSubmissionTimestamp = None,
          acknowledgedTimestamp = None,
          lastAction = Some(dt),
          employmentInfo = None
        )
      )


      val response = client(s"/${regID}/directors").get.futureValue
      response.status shouldBe 403
    }

    "Return a 403 when the user is not authorised to upsert directors" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val transactionID = "NN1234"
      val intID = "Int-xxx-yyy-zzz"
      val timestamp = "2017-01-01T00:00:00"
      upsertToDb(
        PAYERegistration(
          registrationID = regID,
          transactionID = transactionID,
          internalID = intID,
          acknowledgementReference = Some("testAckRef"),
          crn = None,
          registrationConfirmation = None,
          formCreationTimestamp = timestamp,
          status = PAYEStatus.draft,
          completionCapacity = None,
          companyDetails = None,
          directors = Seq.empty,
          payeContact = None,
          sicCodes = Seq.empty,
          lastUpdate = lastUpdate,
          partialSubmissionTimestamp = None,
          fullSubmissionTimestamp = None,
          acknowledgedTimestamp = None,
          lastAction = Some(dt),
          employmentInfo = None
        )
      )

      val response = client(s"/${regID}/directors")
        .patch(Json.toJson(validDirectors)(Director.directorSequenceWriter(APIValidation)))
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
