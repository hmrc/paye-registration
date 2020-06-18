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


import java.time.{ZoneOffset, ZonedDateTime}

import auth.CryptoSCRS
import com.kenshoo.play.metrics.Metrics
import enums.PAYEStatus
import helpers.DateHelper
import itutil.{IntegrationSpecBase, WiremockHelper}
import models._
import models.validation.{APIValidation, MongoValidation}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Format, JsValue, Json}
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.RegistrationMongoRepository

import scala.concurrent.ExecutionContext.Implicits.global

class CompanyDetailsISpec extends IntegrationSpecBase {

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

  class Setup  {
    lazy val mockMetrics = app.injector.instanceOf[Metrics]
    lazy val mockDateHelper = app.injector.instanceOf[DateHelper]
    lazy val mockcryptoSCRS = app.injector.instanceOf[CryptoSCRS]

    val repository = new RegistrationMongoRepository(mockMetrics, mockDateHelper, reactiveMongoComponent, sConfig, mockcryptoSCRS)

    def upsertToDb(paye: PAYERegistration) = await(repository.updateRegistration(paye))

    await(repository.drop)
    await(repository.ensureIndexes)
  }


  "PAYE Registration API - Company Details" should {
    val lastUpdate = "2017-05-09T07:58:35Z"
    val dt = ZonedDateTime.of(2000,1,20,16,1,0,0,ZoneOffset.UTC)

    val validCompanyDetails = CompanyDetails(
      companyName = "Test Company Name",
      tradingName = Some("Test Trading Name"),
      roAddress = Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), None),
      ppobAddress = Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK")),
      businessContactDetails = DigitalContactDetails(Some("test@email.com"), Some("0123459999"), Some("9876549999"))
    )

    val oldFormatCompanyDetails = {
      implicit val format = CompanyDetails.format(MongoValidation)
      CompanyDetails(
        companyName = "Test Company Name",
        tradingName = Some("Test Trading Name"),
        roAddress = Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), None),
        ppobAddress = Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK")),
        businessContactDetails = DigitalContactDetails(Some("test@email.com"), Some("012345"), Some("987654"))
      )
    }

    "Return a 200 when the user gets company details" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val txID = "NN1234"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"
      upsertToDb(
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
          companyDetails = Some(validCompanyDetails),
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

      val response = client(s"/${regID}/company-details").get.futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson(validCompanyDetails)(CompanyDetails.format(MongoValidation))
    }

    "Return a 200 when the user gets company details with an old format" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val txID = "NN1234"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"
      upsertToDb(
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
          companyDetails = Some(oldFormatCompanyDetails),
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

      val response = client(s"/${regID}/company-details").get.futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson(oldFormatCompanyDetails)(CompanyDetails.format(MongoValidation))
    }

    "Return a 200 when the user upserts company details with a company name that contains none standard characters" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val txID = "NN1234"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"
      repository.upsertRegTestOnly(
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

      val validCompanyDetails = new CompanyDetails(
        companyName = "Téšt Çômpåñÿ Ñämę",
        tradingName = Some("Test Trading Name"),
        roAddress = Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), None),
        ppobAddress = Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK")),
        businessContactDetails = DigitalContactDetails(Some("test@email.com"), Some("0123459999"), Some("9876549999"))
      )

      val getResponse1 = client(s"/${regID}/company-details").get.futureValue
      getResponse1.status shouldBe 404

      val patchResponse = client(s"/${regID}/company-details")
        .patch[JsValue](Json.toJson(validCompanyDetails)(CompanyDetails.format(APIValidation)))
        .futureValue
      patchResponse.status shouldBe 200

      val getResponse2 = client(s"/${regID}/company-details").get.futureValue
      getResponse2.status shouldBe 200
      getResponse2.json shouldBe Json.toJson(validCompanyDetails)(CompanyDetails.format(MongoValidation))

      await(repository.retrieveRegistration(regID)).get.companyDetails.get.companyName shouldBe "Téšt Çômpåñÿ Ñämę"
    }

    "Return a 200 when the user upserts company details" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val txID = "NN1234"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"
      upsertToDb(
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
          directors = Seq.empty,
          payeContact = None,
          sicCodes = Seq.empty,
          lastUpdate = lastUpdate,
          partialSubmissionTimestamp = None,
          fullSubmissionTimestamp = None,
          acknowledgedTimestamp = None,
          lastAction = Some(dt),
          employmentInfo = None)
        )



      val getResponse1 = client(s"/${regID}/company-details").get.futureValue
      getResponse1.status shouldBe 404

      val patchResponse = client(s"/${regID}/company-details")
        .patch[JsValue](Json.toJson(validCompanyDetails)(CompanyDetails.format(APIValidation)))
        .futureValue
      patchResponse.status shouldBe 200

      val getResponse2 = client(s"/${regID}/company-details").get.futureValue
      getResponse2.status shouldBe 200
      getResponse2.json shouldBe Json.toJson(validCompanyDetails)(CompanyDetails.format(MongoValidation))
    }

    "Return a 403 when the user is not authorised to get company details" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val txID = "NN1234"
      val intID = "Int-xxx-yyy-zzz"
      val timestamp = "2017-01-01T00:00:00"
      upsertToDb(
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

      val response = client(s"/${regID}/company-details").get.futureValue
      response.status shouldBe 403
    }

    "Return a 403 when the user is not authorised to upsert company details" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val txID = "NN1234"
      val intID = "Int-xxx-yyy-zzz"
      val timestamp = "2017-01-01T00:00:00"
      upsertToDb(
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

      val response = client(s"/${regID}/company-details")
        .patch(Json.toJson(validCompanyDetails)(CompanyDetails.format(APIValidation)))
        .futureValue
      response.status shouldBe 403
    }
  }
}