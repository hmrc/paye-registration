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

import itutil.{IntegrationSpecBase, WiremockHelper}
import models.{Director, Name, PAYERegistration}
import play.api.{Application, Play}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WS
import repositories.RegistrationMongo
import services.MetricsService

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

  private def client(path: String) = WS.url(s"http://localhost:$port/paye-registration$path").withFollowRedirects(false)

  class Setup {
    lazy val mockMetrics = Play.current.injector.instanceOf[MetricsService]
    val mongo = new RegistrationMongo(mockMetrics)
    val repository = mongo.store
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  "PAYE Registration API - Directors" should {
    def setupSimpleAuthMocks() = {
      stubPost("/write/audit", 200, """{"x":2}""")
      stubGet("/auth/authority", 200, """{"uri":"xxx","credentials":{"gatewayId":"xxx2"},"userDetailsLink":"xxx3","ids":"/auth/ids"}""")
      stubGet("/auth/ids", 200, """{"internalId":"Int-xxx","externalId":"Ext-xxx"}""")
    }

    val validDirectors = Seq(
      Director(
        Name(
          forename = Some("Thierry"),
          otherForenames = Some("Dominique"),
          surname = Some("Henry"),
          title = Some("Sir")
        ),
        Some("AA123456Z")
      ),
      Director(
        Name(
          forename = Some("David"),
          otherForenames = Some("Jesus"),
          surname = Some("Trezeguet"),
          title = Some("Mr")
        ),
        Some("AA000009Z")
      )
    )

    "Return a 200 when the user gets directors" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"
      repository.insert(PAYERegistration(regID, intID, timestamp, None, None, validDirectors, None, None, Seq.empty))

      val response = client(s"/${regID}/directors").get.futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson(validDirectors)
    }

    "Return a 200 when the user upserts directors" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"
      repository.insert(PAYERegistration(regID, intID, timestamp, None, None, Seq.empty, None, None, Seq.empty))

      val getResponse1 = client(s"/${regID}/directors").get.futureValue
      getResponse1.status shouldBe 404

      val patchResponse = client(s"/${regID}/directors")
        .patch[JsValue](Json.toJson(validDirectors))
        .futureValue
      patchResponse.status shouldBe 200

      val getResponse2 = client(s"/${regID}/directors").get.futureValue
      getResponse2.status shouldBe 200
      getResponse2.json shouldBe Json.toJson(validDirectors)
    }

    "Return a 403 when the user is not authorised to get directors" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val intID = "Int-xxx-yyy-zzz"
      val timestamp = "2017-01-01T00:00:00"
      repository.insert(PAYERegistration(regID, intID, timestamp, None, None, Seq.empty, None, None, Seq.empty))

      val response = client(s"/${regID}/directors").get.futureValue
      response.status shouldBe 403
    }

    "Return a 403 when the user is not authorised to upsert directors" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val intID = "Int-xxx-yyy-zzz"
      val timestamp = "2017-01-01T00:00:00"
      repository.insert(PAYERegistration(regID, intID, timestamp, None, None, Seq.empty, None, None, Seq.empty))

      val response = client(s"/${regID}/directors")
        .patch(Json.toJson(validDirectors))
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
