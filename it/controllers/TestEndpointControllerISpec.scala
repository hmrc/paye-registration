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

import itutil.{IntegrationSpecBase, WiremockHelper}
import models._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WS
import repositories.{RegistrationMongo, RegistrationMongoRepository}

import scala.concurrent.ExecutionContext.Implicits.global

class TestEndpointControllerISpec extends IntegrationSpecBase {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.auth.host" -> s"$mockHost",
    "microservice.services.auth.port" -> s"$mockPort",
    "application.router" -> "testOnlyDoNotUseInAppConf.Routes"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build

  private def client(path: String) = WS.url(s"http://localhost:$port/paye-registration/test-only$path").withFollowRedirects(false)

  class Setup {
    val mongo = new RegistrationMongo()
    val repository: RegistrationMongoRepository = mongo.store
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  "registration-teardown" should {
    "return a 200 when the collection is dropped" in new Setup {
      val regID1 = "12345"
      val regID2 = "12346"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"

      await(repository.insert(PAYERegistration(regID1, intID, timestamp, None, None, Seq.empty, None, None, Seq.empty)))
      await(repository.insert(PAYERegistration(regID2, intID, timestamp, None, None, Seq.empty, None, None, Seq.empty)))

      await(repository.count) shouldBe 2

      val response = client(s"/registration-teardown").get.futureValue
      response.status shouldBe 200

      await(repository.count) shouldBe 0
    }
  }

  "delete-registration" should {
    "return a 200 when a specified registration has been removed" in new Setup {
      val regID1 = "12345"
      val regID2 = "12346"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"

      await(repository.insert(PAYERegistration(regID1, intID, timestamp, None, None, Seq.empty, None, None, Seq.empty)))
      await(repository.insert(PAYERegistration(regID2, intID, timestamp, None, None, Seq.empty, None, None, Seq.empty)))

      await(repository.count) shouldBe 2

      val response = client(s"/delete-registration/$regID1").get.futureValue
      response.status shouldBe 200

      await(repository.count) shouldBe 1
    }
  }

  "update-registration" should {

    def setupSimpleAuthMocks() = {
      stubPost("/write/audit", 200, """{"x":2}""")
      stubGet("/auth/authority", 200, """{"uri":"xxx","credentials":{"gatewayId":"xxx2"},"userDetailsLink":"xxx3","ids":"/auth/ids"}""")
      stubGet("/auth/ids", 200, """{"internalId":"Int-xxx","externalId":"Ext-xxx"}""")
    }

    "return a 200 when a specified registration has been updated" ignore new Setup {
      // Doesn't seem to work! as expected - TODO SCRS-4976
      setupSimpleAuthMocks()
      val regID1 = "12345"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"

      await(repository.insert(PAYERegistration(regID1, intID, timestamp, None, None, Seq.empty, None, None, Seq.empty)))
      await(repository.count) shouldBe 1

      val jsonBody = Json.toJson[PAYERegistration](
        PAYERegistration(
          regID1,
          intID,
          timestamp,
          Some("Director"),
          Some(
            CompanyDetails(
              Some("1234567890"),
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
              Some("AA123456Z")
            )
          ),
          Some(
            PAYEContact(
              name = "Thierry Henry",
              digitalContactDetails = DigitalContactDetails(
                Some("test@test.com"),
                Some("1234"),
                Some("4358475")
              ),
              payeCorrespondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK"))
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
      )

      val response = client(s"/update-registration/$regID1").post[JsObject](jsonBody.as[JsObject]).futureValue
      response.status shouldBe 200
    }

    "return a 400 when the json body cannot be validated" in new Setup {
      setupSimpleAuthMocks()

      val regID1 = "12345"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"

      await(repository.insert(PAYERegistration(regID1, intID, timestamp, None, None, Seq.empty, None, None, Seq.empty)))
      await(repository.count) shouldBe 1

      val response = client(s"/update-registration/$regID1").post(Json.toJson("""{"invalid" : "data"}""")).futureValue
      response.status shouldBe 400
      println(response.body)
    }
  }
}
