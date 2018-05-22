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

import com.kenshoo.play.metrics.Metrics
import enums.PAYEStatus
import helpers.DateHelper
import itutil.{IntegrationSpecBase, WiremockHelper}
import models._
import models.validation.{APIValidation, MongoValidation}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.{Application, Configuration}
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.{RegistrationMongo, RegistrationMongoRepository}

import scala.concurrent.ExecutionContext.Implicits.global

class PAYEContactISpec extends IntegrationSpecBase {

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
    val repository: RegistrationMongoRepository = mongo.store

    def insertToDb(paye: PAYERegistration) = {
      await(repository.insert(paye))
      await(repository.count) shouldBe 1
    }

    await(repository.drop)
    await(repository.ensureIndexes)
  }

  "PAYE Registration API - PAYE Contact" should {
    val lastUpdate = "2017-05-09T07:58:35Z"

    val validPAYEContact = PAYEContact(
      contactDetails = PAYEContactDetails(
        name = "Thierry Henry",
        digitalContactDetails = DigitalContactDetails(Some("test@email.com"), Some("0123459999"), Some("9876549999"))
      ),
      correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), None)
    )

    val oldFormatPAYEContact = {
      implicit val format = PAYEContact.format(MongoValidation)
      PAYEContact(
        contactDetails = PAYEContactDetails(
          name = "Thierry Henry",
          digitalContactDetails = DigitalContactDetails(Some("test@email.com"), Some("012345"), Some("987654"))
        ),
        correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), None)
      )
    }

    "Return a 200 when the user gets paye contact" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val transactionID = "NN1234"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"
      insertToDb(PAYERegistration(
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
        Some(validPAYEContact),
        Seq.empty,
        lastUpdate,
        partialSubmissionTimestamp = None,
        fullSubmissionTimestamp = None,
        acknowledgedTimestamp = None,
        lastAction = None,
        employmentInfo = None
      ))

      val response = client(s"/${regID}/contact-correspond-paye").get.futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson(validPAYEContact)(PAYEContact.format(APIValidation))
    }

    "Return a 200 when the user gets paye contact with old format" in new Setup {
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
          Some(Eligibility(false, false)),
          PAYEStatus.draft,
          None,
          None,
          Seq.empty,
          Some(oldFormatPAYEContact),
          Seq.empty,
          lastUpdate,
          partialSubmissionTimestamp = None,
          fullSubmissionTimestamp = None,
          acknowledgedTimestamp = None,
          lastAction = None,
          employmentInfo = None
        )
      )

      val response = client(s"/${regID}/contact-correspond-paye").get.futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson(oldFormatPAYEContact)(PAYEContact.format(APIValidation))
    }

    "Return a 200 when the user upserts paye contact" in new Setup {
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
          Some(Eligibility(false, false)),
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

      val getResponse1 = client(s"/${regID}/contact-correspond-paye").get.futureValue
      getResponse1.status shouldBe 404

      val patchResponse = client(s"/${regID}/contact-correspond-paye")
        .patch[JsValue](Json.toJson(validPAYEContact)(PAYEContact.format(APIValidation)))
        .futureValue
      patchResponse.status shouldBe 200

      val getResponse2 = client(s"/${regID}/contact-correspond-paye").get.futureValue
      getResponse2.status shouldBe 200
      getResponse2.json shouldBe Json.toJson(validPAYEContact)(PAYEContact.format(APIValidation))
    }

    "Return a 403 when the user is not authorised to get paye contact" in new Setup {
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
          Some(Eligibility(false, false)),
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

      val response = client(s"/${regID}/contact-correspond-paye").get.futureValue
      response.status shouldBe 403
    }

    "Return a 403 when the user is not authorised to upsert paye contact" in new Setup {
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
          Some(Eligibility(false, false)),
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

      val response = client(s"/${regID}/contact-correspond-paye")
        .patch(Json.toJson(validPAYEContact)(PAYEContact.format(APIValidation)))
        .futureValue
      response.status shouldBe 403
    }
  }
}