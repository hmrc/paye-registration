package api

import itutil.{IntegrationSpecBase, WiremockHelper}
import models.{DigitalContactDetails, PAYEContact, PAYERegistration}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WS
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

  private def client(path: String) = WS.url(s"http://localhost:$port/paye-registration$path").withFollowRedirects(false)

  class Setup {
    val mongo = new RegistrationMongo()
    val repository: RegistrationMongoRepository = mongo.store
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  "PAYE Registration API - PAYE Contact" should {
    def setupSimpleAuthMocks() = {
      stubPost("/write/audit", 200, """{"x":2}""")
      stubGet("/auth/authority", 200, """{"uri":"xxx","credentials":{"gatewayId":"xxx2"},"userDetailsLink":"xxx3","ids":"/auth/ids"}""")
      stubGet("/auth/ids", 200, """{"internalId":"Int-xxx","externalId":"Ext-xxx"}""")
    }

    val validPAYEContact = new PAYEContact(
      name = "Thierry Henry",
      digitalContact = DigitalContactDetails(Some("test@email.com"), Some("012345"), Some("987654"))
    )

    "Return a 200 when the user gets paye contact" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"
      repository.insert(PAYERegistration(regID, intID, timestamp, None, Seq.empty, Some(validPAYEContact), None, Seq.empty))

      val response = client(s"/${regID}/contact-paye").get.futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson(validPAYEContact)
    }

    "Return a 200 when the user upserts paye contact" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val intID = "Int-xxx"
      val timestamp = "2017-01-01T00:00:00"
      repository.insert(PAYERegistration(regID, intID, timestamp, None, Seq.empty, None, None, Seq.empty))

      val getResponse1 = client(s"/${regID}/contact-paye").get.futureValue
      getResponse1.status shouldBe 404

      val patchResponse = client(s"/${regID}/contact-paye")
        .patch[JsValue](Json.toJson(validPAYEContact))
        .futureValue
      patchResponse.status shouldBe 200

      val getResponse2 = client(s"/${regID}/contact-paye").get.futureValue
      getResponse2.status shouldBe 200
      getResponse2.json shouldBe Json.toJson(validPAYEContact)
    }

    "Return a 403 when the user is not authorised to get paye contact" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val intID = "Int-xxx-yyy-zzz"
      val timestamp = "2017-01-01T00:00:00"
      repository.insert(PAYERegistration(regID, intID, timestamp, None, Seq.empty, None, None, Seq.empty))

      val response = client(s"/${regID}/contact-paye").get.futureValue
      response.status shouldBe 403
    }

    "Return a 403 when the user is not authorised to upsert paye contact" in new Setup {
      setupSimpleAuthMocks()

      val regID = "12345"
      val intID = "Int-xxx-yyy-zzz"
      val timestamp = "2017-01-01T00:00:00"
      repository.insert(PAYERegistration(regID, intID, timestamp, None, Seq.empty, None, None, Seq.empty))

      val response = client(s"/${regID}/contact-paye")
        .patch(Json.toJson(validPAYEContact))
        .futureValue
      response.status shouldBe 403
    }
  }
}
