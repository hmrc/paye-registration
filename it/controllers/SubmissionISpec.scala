package controllers

import java.time.LocalDate

import com.github.tomakehurst.wiremock.client.WireMock._
import enums.PAYEStatus
import itutil.{WiremockHelper, IntegrationSpecBase}
import models._
import models.external.BusinessProfile
import play.api.{Play, Application}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WS
import repositories.{SequenceMongoRepository, RegistrationMongoRepository, SequenceMongo, RegistrationMongo}
import services.MetricsService

import scala.concurrent.ExecutionContext.Implicits.global

class SubmissionISpec extends IntegrationSpecBase {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.auth.host" -> s"$mockHost",
    "microservice.services.auth.port" -> s"$mockPort",
    "microservice.services.des-stub.port" -> s"$mockPort",
    "microservice.services.des-stub.url" -> s"$mockHost",
    "microservice.services.des-service.url" -> s"$mockHost",
    "microservice.services.des-service.port" -> s"$mockPort",
    "application.router" -> "testOnlyDoNotUseInAppConf.Routes",
    "microservice.services.incorporation-information.host" -> s"$mockHost",
    "microservice.services.incorporation-information.port" -> s"$mockPort",
    "microservice.services.business-registration.host" -> s"$mockHost",
    "microservice.services.business-registration.port" -> s"$mockPort",
    "microservice.services.company-registration.host" -> s"$mockHost",
    "microservice.services.company-registration.port" -> s"$mockPort"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  private def client(path: String) = WS.url(s"http://localhost:$port/paye-registration/$path")
    .withFollowRedirects(false)
    .withHeaders(("X-Session-ID","session-12345"))

  private val regime = "paye"
  private val subscriber = "SCRS"

  class Setup {
    lazy val mockMetrics = Play.current.injector.instanceOf[MetricsService]
    val mongo = new RegistrationMongo(mockMetrics)
    val sequenceMongo = new SequenceMongo()
    val repository: RegistrationMongoRepository = mongo.store
    val sequenceRepository: SequenceMongoRepository = sequenceMongo.store
    await(repository.drop)
    await(repository.ensureIndexes)
    await(sequenceRepository.drop)
    await(sequenceRepository.ensureIndexes)
  }

  val regId = "12345"
  val transactionID = "NN1234"
  val intId = "Int-xxx"
  val timestamp = "2017-01-01T00:00:00"

  val businessProfile = BusinessProfile(regId, completionCapacity = None, language = "en")
  def stubBusinessProfile() = stubFor(
    get(urlMatching("/business-registration/business-tax-registration"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(Json.toJson(businessProfile).toString())
      )
    )

  val submission = PAYERegistration(
    regId,
    transactionID,
    intId,
    Some("testAckRef"),
    None,
    None,
    timestamp,
    Some(Eligibility(false, false)),
    PAYEStatus.draft,
    Some("Director"),
    Some(
      CompanyDetails(
        "testCompanyName",
        Some("test"),
        Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), None),
        Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK")),
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
        Some("SR123456C")
      )
    ),
    Some(
      PAYEContact(
        contactDetails = PAYEContactDetails(
          name = "Thierry Henry",
          digitalContactDetails = DigitalContactDetails(
            Some("test@test.com"),
            Some("1234"),
            Some("4358475")
          )
        ),
        correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), None, Some("UK"))
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
      SICCode(code = None, description = Some("consulting"))
    )
  )
  val processedSubmission = PAYERegistration(
    regId,
    transactionID,
    intId,
    Some("testAckRef"),
    None,
    None,
    timestamp,
    eligibility = Some(Eligibility(false, false)),
    PAYEStatus.held,
    None,
    None,
    Nil,
    None,
    None,
    Nil
  )

  val crn = "OC123456"
  val accepted = "accepted"
  val rejected = "rejected"

  def incorpUpdate(status: String) = {
    s"""
       |{
       |  "SCRSIncorpStatus": {
       |    "IncorpSubscriptionKey" : {
       |      "subscriber" : "SCRS",
       |      "discriminator" : "PAYE",
       |      "transactionId" : "$transactionID"
       |    },
       |    "SCRSIncorpSubscription" : {
       |      "callbackUrl" : "scrs-incorporation-update-listener.service/incorp-updates/incorp-status-update"
       |    },
       |    "IncorpStatusEvent": {
       |      "status": "$status",
       |      "crn":"$crn",
       |      "incorporationDate":"2000-12-12",
       |      "timestamp" : ${Json.toJson(LocalDate.of(2017, 12, 21))}
       |    }
       |  }
       |}
        """.stripMargin
  }

  val rejectedSubmission = submission.copy(status = PAYEStatus.cancelled)

  "submit-registration" should {
    "return a 200 with an ack ref when a partial DES submission completes successfully" in new Setup {
      setupSimpleAuthMocks()

      val regime = "paye"
      val subscriber = "SCRS"

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(200)
        )
      )

      stubBusinessProfile()

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      val extraDirectorList = submission.directors :+ Director(
        name = Name(forename = Some("Malcolm"), surname = Some("Test"), otherForenames = Some("Testing"), title = Some("Mr")),
        nino = None
      )
      await(repository.insert(submission.copy(directors = extraDirectorList)))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"$regId/submit-registration").put("").futureValue

      verify(postRequestedFor(urlEqualTo("/business-registration/pay-as-you-earn"))
        .withRequestBody(equalToJson(Json.parse(
          s"""
             |{
             | "acknowledgementReference": "testAckRef",
             |    "metaData": {
             |        "businessType": "Limited company",
             |        "sessionID": "session-12345",
             |        "credentialID": "xxx2",
             |        "language": "en",
             |        "formCreationTimestamp": "$timestamp",
             |        "submissionFromAgent": false,
             |        "completionCapacity": "director",
             |        "declareAccurateAndComplete": true
             |    },
             |    "payAsYouEarn": {
             |        "limitedCompany": {
             |            "companiesHouseCompanyName": "testCompanyName",
             |            "nameOfBusiness": "test",
             |            "registeredOfficeAddress": {
             |                "addressLine1": "14 St Test Walk",
             |                "addressLine2": "Testley",
             |                "addressLine3": "Testford",
             |                "addressLine4": "Testshire",
             |                "postcode": "TE1 1ST"
             |            },
             |            "businessAddress": {
             |                "addressLine1": "14 St Test Walk",
             |                "addressLine2": "Testley",
             |                "addressLine3": "Testford",
             |                "addressLine4": "Testshire",
             |                "country": "UK"
             |            },
             |            "businessContactDetails": {
             |                "phoneNumber": "012345",
             |                "mobileNumber": "543210",
             |                "email": "test@email.com"
             |            },
             |            "natureOfBusiness": "consulting",
             |            "directors": [
             |                {
             |                   "directorName": {
             |                     "title": "Sir",
             |              	      "firstName": "Thierry",
             |              	      "lastName": "Henry",
             |              	      "middleName": "Dominique"
             |                   },
             |                   "directorNINO": "SR123456C"
             |                },
             |                {
             |                   "directorName": {
             |                     "title": "Mr",
             |              	      "firstName": "Malcolm",
             |              	      "lastName": "Test",
             |              	      "middleName": "Testing"
             |                   }
             |                }
             |            ],
             |            "operatingOccPensionScheme": true
             |        },
             |        "employingPeople": {
             |            "dateOfFirstEXBForEmployees": "2016-12-20",
             |            "numberOfEmployeesExpectedThisYear": "1",
             |            "engageSubcontractors": true,
             |            "correspondenceName": "Thierry Henry",
             |            "correspondenceContactDetails": {
             |                "phoneNumber": "1234",
             |                "mobileNumber": "4358475",
             |                "email": "test@test.com"
             |            },
             |            "payeCorrespondenceAddress": {
             |                "addressLine1": "19 St Walk",
             |                "addressLine2": "Testley CA",
             |                "addressLine3": "Testford",
             |                "addressLine4": "Testshire",
             |                "country": "UK"
             |            }
             |        }
             |    }
             |}
          """.stripMargin).toString())
        )
      )

      response.status shouldBe 200
      response.json shouldBe Json.toJson("testAckRef")

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedSubmission)
    }

    "return a 200 with an ack ref when a full DES submission completes successfully" in new Setup {
      setupSimpleAuthMocks()

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(200)
        )
      )

      stubBusinessProfile()

      stubFor(post(urlMatching(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              incorpUpdate(accepted)
            )
        )
      )

      stubFor(get(urlMatching(s"/company-registration/corporation-tax-registration/12345/corporation-tax-registration"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """{
                | "acknowledgementReferences" : {
                |   "ctUtr" : "testCtUtr"
                | }
                |}
                |""".stripMargin
            )
        )
      )

      await(repository.insert(submission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = await(client(s"$regId/submit-registration").put(""))

      verify(postRequestedFor(urlEqualTo("/business-registration/pay-as-you-earn"))
        .withRequestBody(equalToJson(Json.parse(
          s"""
             |{
             | "acknowledgementReference": "testAckRef",
             |    "metaData": {
             |        "businessType": "Limited company",
             |        "sessionID": "session-12345",
             |        "credentialID": "xxx2",
             |        "language": "en",
             |        "formCreationTimestamp": "$timestamp",
             |        "submissionFromAgent": false,
             |        "completionCapacity": "director",
             |        "declareAccurateAndComplete": true
             |    },
             |    "payAsYouEarn": {
             |        "limitedCompany": {
             |            "companyUTR": "testCtUtr",
             |            "crn": "OC123456",
             |            "companiesHouseCompanyName": "testCompanyName",
             |            "nameOfBusiness": "test",
             |            "registeredOfficeAddress": {
             |                "addressLine1": "14 St Test Walk",
             |                "addressLine2": "Testley",
             |                "addressLine3": "Testford",
             |                "addressLine4": "Testshire",
             |                "postcode": "TE1 1ST"
             |            },
             |            "businessAddress": {
             |                "addressLine1": "14 St Test Walk",
             |                "addressLine2": "Testley",
             |                "addressLine3": "Testford",
             |                "addressLine4": "Testshire",
             |                "country": "UK"
             |            },
             |            "businessContactDetails": {
             |                "phoneNumber": "012345",
             |                "mobileNumber": "543210",
             |                "email": "test@email.com"
             |            },
             |            "natureOfBusiness": "consulting",
             |            "directors": [
             |                {
             |                   "directorName": {
             |                     "title": "Sir",
             |              	      "firstName": "Thierry",
             |              	      "lastName": "Henry",
             |              	      "middleName": "Dominique"
             |                   },
             |                   "directorNINO": "SR123456C"
             |                }
             |            ],
             |            "operatingOccPensionScheme": true
             |        },
             |        "employingPeople": {
             |            "dateOfFirstEXBForEmployees": "2016-12-20",
             |            "numberOfEmployeesExpectedThisYear": "1",
             |            "engageSubcontractors": true,
             |            "correspondenceName": "Thierry Henry",
             |            "correspondenceContactDetails": {
             |                "phoneNumber": "1234",
             |                "mobileNumber": "4358475",
             |                "email": "test@test.com"
             |            },
             |            "payeCorrespondenceAddress": {
             |                "addressLine1": "19 St Walk",
             |                "addressLine2": "Testley CA",
             |                "addressLine3": "Testford",
             |                "addressLine4": "Testshire",
             |                "country": "UK"
             |            }
             |        }
             |    }
             |}
          """.stripMargin).toString())
        )
      )

      response.status shouldBe 200
      response.json shouldBe Json.toJson("testAckRef")

      await(repository.retrieveRegistration(regId)).get.status shouldBe PAYEStatus.submitted
    }

    "return a 200 status with an ackRef when DES returns a 409" in new Setup {
      setupSimpleAuthMocks()

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody("""{"acknowledgement_reference" : "testAckRef"}""")
        )
      )

      stubFor(post(urlMatching(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber"))
        .willReturn(
          aResponse()
            .withStatus(202)
        )
      )

      stubBusinessProfile()


      await(repository.insert(submission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"$regId/submit-registration").put("").futureValue
      response.status shouldBe 200
      response.json shouldBe Json.toJson("testAckRef")

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedSubmission)
    }

    "return a 204 status when Incorporation was rejected at PAYE Submission" in new Setup {
      setupSimpleAuthMocks()

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 200, incorpUpdate(rejected))

      await(repository.insert(submission))

      val response = client(s"$regId/submit-registration").put("").futureValue
      response.status shouldBe 204

      await(repository.retrieveRegistration(regId)) shouldBe Some(rejectedSubmission)
    }

    "return a 502 status when DES returns a 499" in new Setup {
      setupSimpleAuthMocks()

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(499)
        )
      )

      stubBusinessProfile()

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      await(repository.insert(submission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"$regId/submit-registration").put("").futureValue
      response.status shouldBe 502

      await(repository.retrieveRegistration(regId)) shouldBe Some(submission)
    }

    "return a 502 status when DES returns a 5xx" in new Setup {
      setupSimpleAuthMocks()

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(533)
        )
      )

      stubBusinessProfile()

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      await(repository.insert(submission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"$regId/submit-registration").put("").futureValue
      response.status shouldBe 502

      await(repository.retrieveRegistration(regId)) shouldBe Some(submission)
    }

    "return a 400 status when DES returns a 4xx" in new Setup {
      setupSimpleAuthMocks()

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(433)
        )
      )

      stubBusinessProfile()

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      await(repository.insert(submission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"$regId/submit-registration").put("").futureValue
      response.status shouldBe 400

      await(repository.retrieveRegistration(regId)) shouldBe Some(submission)
    }

    "return a 500 status when registration has already been cleared post-submission in mongo" in new Setup {
      setupSimpleAuthMocks()

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      await(repository.insert(processedSubmission))
      await(client(s"test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"$regId/submit-registration").put("").futureValue
      response.status shouldBe 500

      await(repository.retrieveRegistration(regId)) shouldBe Some(processedSubmission)
    }
  }
}
