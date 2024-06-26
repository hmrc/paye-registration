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

package test.api

import auth.CryptoSCRS
import com.codahale.metrics.MetricRegistry
import enums.PAYEStatus
import helpers.DateHelper
import itutil.{IntegrationSpecBase, WiremockHelper}
import models._
import models.validation.{APIValidation, MongoValidation}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import repositories.RegistrationMongoRepository
import uk.gov.hmrc.mongo.MongoComponent

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
    .build()

  lazy val mongoComponent = app.injector.instanceOf[MongoComponent]
  lazy val sConfig = app.injector.instanceOf[Configuration]
  lazy val mockcryptoSCRS = app.injector.instanceOf[CryptoSCRS]

  class Setup {
    lazy val mockMetricRegistry = app.injector.instanceOf[MetricRegistry]
    lazy val mockDateHelper = app.injector.instanceOf[DateHelper]
    val repository = new RegistrationMongoRepository(mockMetricRegistry, mockDateHelper, mongoComponent, sConfig, mockcryptoSCRS)

    def insertToDb(paye: PAYERegistration) = {
      await(repository.updateRegistration(paye))
      await(repository.collection.countDocuments().toFuture()) mustBe 1
    }

    await(repository.dropCollection)
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

      val response = client(s"/${regID}/contact-correspond-paye").get().futureValue
      response.status mustBe 200
      response.json mustBe Json.toJson(validPAYEContact)(PAYEContact.format(APIValidation))
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

      val response = client(s"/${regID}/contact-correspond-paye").get().futureValue
      response.status mustBe 200
      response.json mustBe Json.toJson(oldFormatPAYEContact)(PAYEContact.format(APIValidation))
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

      val getResponse1 = client(s"/${regID}/contact-correspond-paye").get().futureValue
      getResponse1.status mustBe 404

      val patchResponse = client(s"/${regID}/contact-correspond-paye")
        .patch[JsValue](Json.toJson(validPAYEContact)(PAYEContact.format(APIValidation)))
        .futureValue
      patchResponse.status mustBe 200

      val getResponse2 = client(s"/${regID}/contact-correspond-paye").get().futureValue
      getResponse2.status mustBe 200
      getResponse2.json mustBe Json.toJson(validPAYEContact)(PAYEContact.format(APIValidation))
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

      val response = client(s"/${regID}/contact-correspond-paye").get().futureValue
      response.status mustBe 403
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
      response.status mustBe 403
    }
  }
}