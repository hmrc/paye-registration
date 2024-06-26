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

package controllers

import java.time.{LocalDate, ZoneOffset, ZonedDateTime}
import auth.CryptoSCRS
import com.github.tomakehurst.wiremock.client.WireMock._
import com.codahale.metrics.MetricRegistry
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import enums.{Employing, PAYEStatus}
import fixtures.EmploymentInfoFixture
import helpers.DateHelper
import itutil.{IntegrationSpecBase, WiremockHelper}
import models._
import models.external.BusinessProfile
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import repositories.{RegistrationMongoRepository, SequenceMongoRepository}
import uk.gov.hmrc.mongo.MongoComponent
import utils.SystemDate

import scala.concurrent.ExecutionContext.Implicits.global

class SubmissionISpec extends IntegrationSpecBase with EmploymentInfoFixture {

  val mockHost: String = WiremockHelper.wiremockHost
  val mockPort: Int = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration: Map[String, String] = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.auth.host" -> s"$mockHost",
    "microservice.services.auth.port" -> s"$mockPort",
    "microservice.services.des-stub.port" -> s"$mockPort",
    "microservice.services.des-stub.url" -> s"$mockHost",
    "microservice.services.des-service.url" -> s"$mockUrl",
    "microservice.services.des-service.uri" -> "business-registration/pay-as-you-earn",
    "application.router" -> "testOnlyDoNotUseInAppConf.Routes",
    "microservice.services.incorporation-information.host" -> s"$mockHost",
    "microservice.services.incorporation-information.port" -> s"$mockPort",
    "microservice.services.business-registration.host" -> s"$mockHost",
    "microservice.services.business-registration.port" -> s"$mockPort",
    "microservice.services.company-registration.host" -> s"$mockHost",
    "microservice.services.company-registration.port" -> s"$mockPort",
    "microservice.services.des-service.environment" -> "test-environment",
    "microservice.services.des-service.authorization-token" -> "testAuthToken"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  lazy val mongoComponent: MongoComponent = app.injector.instanceOf[MongoComponent]
  lazy val sConfig: Configuration = app.injector.instanceOf[Configuration]
  lazy val mockcryptoSCRS: CryptoSCRS = app.injector.instanceOf[CryptoSCRS]

  private val regime = "paye"
  private val subscriber = "SCRS"

  val lastUpdate = "2017-05-09T07:58:35Z"
  val dt = ZonedDateTime.of(2000,1,20,16,1,0,0,ZoneOffset.UTC)

  class Setup {
    lazy val mockMetricRegistry: MetricRegistry = app.injector.instanceOf[MetricRegistry]
    lazy val mockDateHelper: DateHelper = app.injector.instanceOf[DateHelper]
    val repository = new RegistrationMongoRepository(mockMetricRegistry, mockDateHelper, mongoComponent, sConfig, mockcryptoSCRS)
    val sequenceRepository = new SequenceMongoRepository(mongoComponent)
    await(repository.dropCollection)
    await(sequenceRepository.collection.drop().toFuture())
    await(sequenceRepository.ensureIndexes())
  }

  val regId = "12345"
  val transactionID = "NN1234"
  val extId = "Ext-xxx"
  val intId = "Int-xxx"
  val timestamp = "2017-01-01T00:00:00"

  val businessProfile: BusinessProfile = BusinessProfile(regId, completionCapacity = None, language = "en")
  def stubBusinessProfile(): StubMapping = stubFor(
    get(urlMatching("/business-registration/business-tax-registration"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(Json.toJson(businessProfile).toString())
      )
    )

  def submission: PAYERegistration = PAYERegistration(
    regId,
    transactionID,
    intId,
    Some("testAckRef"),
    None,
    None,
    timestamp,
    PAYEStatus.draft,
    Some("Director"),
    Some(
      CompanyDetails(
        "testCompanyName",
        Some("test"),
        Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), None, Some("roAuditRef")),
        Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK"), Some("ppobAuditRef")),
        DigitalContactDetails(Some("test@email.com"), Some("0123459999"), Some("5432109999"))
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
            Some("1234999999"),
            Some("4358475999")
          )
        ),
        correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), None, Some("UK"), Some("correspondenceAuditRef"))
      )
    ),
    Seq(
      SICCode(code = None, description = Some("consulting"))
    ),
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(dt),
    employmentInfo = Some(validEmployment)
  )
  val processedSubmission = PAYERegistration(
    regId,
    transactionID,
    intId,
    Some("testAckRef"),
    None,
    None,
    timestamp,
    PAYEStatus.held,
    None,
    None,
    Nil,
    None,
    Nil,
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = None,
    employmentInfo = None
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


  val credId = "xxx2"
  val authoriseData =
    s"""{
       | "internalId": "$intId",
       | "externalId": "$extId",
       | "optionalCredentials": {
       |   "providerId": "$credId",
       |   "providerType": "some-provider-type"
       | }
       |}""".stripMargin

  "submit-registration" should {
    "return a 200 with an ack ref when a partial DES submission completes successfully with auditing" in new Setup {
      setupAuthMocksToReturn(authoriseData)

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
      await(repository.updateRegistration(submission.copy(directors = extraDirectorList)))
      await(client(s"/test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"/$regId/submit-registration").put("").futureValue

      verify(postRequestedFor(urlEqualTo("/business-registration/pay-as-you-earn"))
        .withHeader("Environment", matching("test-environment"))
        .withHeader("Authorization", matching("Bearer testAuthToken"))
        .withRequestBody(equalToJson(Json.parse(
          s"""
             |{
             | "acknowledgementReference": "testAckRef",
             |    "metaData": {
             |        "businessType": "Limited company",
             |        "sessionID": "session-12345",
             |        "credentialID": "$credId",
             |        "language": "en",
             |        "formCreationTimestamp": "$timestamp",
             |        "submissionFromAgent": false,
             |        "completionCapacity": "Director",
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
             |                "phoneNumber": "0123459999",
             |                "mobileNumber": "5432109999",
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
             |            "dateOfFirstEXBForEmployees": "${SystemDate.getSystemDate.toLocalDate}",
             |            "numberOfEmployeesExpectedThisYear": "1",
             |            "engageSubcontractors": true,
             |            "correspondenceName": "Thierry Henry",
             |            "correspondenceContactDetails": {
             |                "phoneNumber": "1234999999",
             |                "mobileNumber": "4358475999",
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


      verify(postRequestedFor(urlEqualTo("/write/audit"))
        .withRequestBody(equalToJson(Json.parse(
          s"""
            |{
            |  "detail" : {
            |    "externalId" : "$extId",
            |    "authProviderId" : "$credId",
            |    "journeyId" : "12345",
            |    "desSubmissionState" : "partial",
            |    "acknowledgementReference" : "testAckRef",
            |    "metaData" : {
            |      "businessType" : "Limited company",
            |      "submissionFromAgent" : false,
            |      "declareAccurateAndComplete" : true,
            |      "sessionID" : "session-12345",
            |      "credentialID" : "$credId",
            |      "language" : "en",
            |      "formCreationTimestamp" : "2017-01-01T00:00:00",
            |      "completionCapacity" : "Director"
            |    },
            |    "payAsYouEarn" : {
            |      "limitedCompany" : {
            |        "companiesHouseCompanyName" : "testCompanyName",
            |        "nameOfBusiness" : "test",
            |        "businessAddress" : {
            |          "addressLine1" : "14 St Test Walk",
            |          "addressLine2" : "Testley",
            |          "addressLine3" : "Testford",
            |          "addressLine4" : "Testshire",
            |          "country" : "UK",
            |          "auditRef" : "ppobAuditRef"
            |        },
            |        "businessContactDetails" : {
            |          "email" : "test@email.com",
            |          "phoneNumber" : "0123459999",
            |          "mobileNumber" : "5432109999"
            |        },
            |        "natureOfBusiness" : "consulting",
            |        "directors" : [ {
            |          "directorName" : {
            |            "firstName" : "Thierry",
            |            "middleName" : "Dominique",
            |            "lastName" : "Henry",
            |            "title" : "Sir"
            |          },
            |          "directorNINO" : "SR123456C"
            |        }, {
            |          "directorName" : {
            |            "firstName" : "Malcolm",
            |            "middleName" : "Testing",
            |            "lastName" : "Test",
            |            "title" : "Mr"
            |          }
            |        } ],
            |        "registeredOfficeAddress" : {
            |          "addressLine1" : "14 St Test Walk",
            |          "addressLine2" : "Testley",
            |          "addressLine3" : "Testford",
            |          "addressLine4" : "Testshire",
            |          "postcode" : "TE1 1ST",
            |          "auditRef" : "roAuditRef"
            |        },
            |        "operatingOccPensionScheme" : true
            |      },
            |      "employingPeople" : {
            |        "dateOfFirstEXBForEmployees" : "${SystemDate.getSystemDate.toLocalDate}",
            |        "numberOfEmployeesExpectedThisYear" : "1",
            |        "engageSubcontractors" : true,
            |        "correspondenceName" : "Thierry Henry",
            |        "correspondenceContactDetails" : {
            |          "email" : "test@test.com",
            |          "phoneNumber" : "1234999999",
            |          "mobileNumber" : "4358475999"
            |        },
            |        "payeCorrespondenceAddress" : {
            |          "addressLine1" : "19 St Walk",
            |          "addressLine2" : "Testley CA",
            |          "addressLine3" : "Testford",
            |          "addressLine4" : "Testshire",
            |          "country" : "UK",
            |          "auditRef" : "correspondenceAuditRef"
            |        }
            |      }
            |    }
            |  }
            |}
          """.stripMargin
        ).toString(), false, true))
      )

      response.status mustBe 200
      response.json mustBe Json.toJson("testAckRef")

      val reg = await(repository.retrieveRegistration(regId))
      reg mustBe Some(processedSubmission.copy(lastUpdate = reg.get.lastUpdate, partialSubmissionTimestamp = reg.get.partialSubmissionTimestamp, lastAction = reg.get.lastAction))

      val regLastUpdate = mockDateHelper.getDateFromTimestamp(reg.get.lastUpdate)
      val submissionLastUpdate = mockDateHelper.getDateFromTimestamp(submission.lastUpdate)

      regLastUpdate.isAfter(submissionLastUpdate) mustBe true
      reg.get.partialSubmissionTimestamp.nonEmpty mustBe true
    }

    "return a 200 with an ack ref when a full DES submission completes successfully" in new Setup {
      setupAuthMocksToReturn(authoriseData)

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

      await(repository.updateRegistration(submission))
      await(client(s"/test-only/feature-flag/desServiceFeature/true").get())

      val response = await(client(s"/$regId/submit-registration").put(""))

      verify(postRequestedFor(urlEqualTo("/business-registration/pay-as-you-earn"))
        .withHeader("Environment", matching("test-environment"))
        .withHeader("Authorization", matching("Bearer testAuthToken"))
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
             |        "completionCapacity": "Director",
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
             |                "phoneNumber": "0123459999",
             |                "mobileNumber": "5432109999",
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
             |            "dateOfFirstEXBForEmployees": "${SystemDate.getSystemDate.toLocalDate}",
             |            "numberOfEmployeesExpectedThisYear": "1",
             |            "engageSubcontractors": true,
             |            "correspondenceName": "Thierry Henry",
             |            "correspondenceContactDetails": {
             |                "phoneNumber": "1234999999",
             |                "mobileNumber": "4358475999",
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

      response.status mustBe 200
      response.json mustBe Json.toJson("testAckRef")


      val reg = await(repository.retrieveRegistration(regId))

      reg.get.status mustBe PAYEStatus.submitted
      reg.get.fullSubmissionTimestamp.nonEmpty mustBe true
    }

    "return a 200 with an ack ref when a full DES submission completes successfully with a company containing none standard characters" in new Setup {
      setupAuthMocksToReturn(authoriseData)

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

      val submission = PAYERegistration(
        regId,
        transactionID,
        intId,
        Some("testAckRef"),
        None,
        None,
        timestamp,
        PAYEStatus.draft,
        Some("Director"),
        Some(
          CompanyDetails(
            "téštÇômpæñÿÑámë[]{}#«»œßøÆŒ",
            Some("test"),
            Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), None, Some("roAuditRef")),
            Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK"), Some("ppobAuditRef")),
            DigitalContactDetails(Some("test@email.com"), Some("0123459999"), Some("5432109999"))
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
                Some("1234999999"),
                Some("4358475999")
              )
            ),
            correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), None, Some("UK"), Some("correspondenceAuditRef"))
          )
        ),
        Seq(
          SICCode(code = None, description = Some("consulting"))
        ),
        lastUpdate,
        partialSubmissionTimestamp = None,
        fullSubmissionTimestamp = None,
        acknowledgedTimestamp = None,
        lastAction = Some(dt),
        employmentInfo = Some(validEmployment)
      )

      await(repository.updateRegistration(submission))
      await(client(s"/test-only/feature-flag/desServiceFeature/true").get())

      val response = await(client(s"/$regId/submit-registration").put(""))

      verify(postRequestedFor(urlEqualTo("/business-registration/pay-as-you-earn"))
        .withHeader("Environment", matching("test-environment"))
        .withHeader("Authorization", matching("Bearer testAuthToken"))
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
             |        "completionCapacity": "Director",
             |        "declareAccurateAndComplete": true
             |    },
             |    "payAsYouEarn": {
             |        "limitedCompany": {
             |            "companyUTR": "testCtUtr",
             |            "crn": "OC123456",
             |            "companiesHouseCompanyName": "testCompaenyNameoessoAEOE",
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
             |                "phoneNumber": "0123459999",
             |                "mobileNumber": "5432109999",
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
             |            "dateOfFirstEXBForEmployees": "${SystemDate.getSystemDate.toLocalDate}",
             |            "numberOfEmployeesExpectedThisYear": "1",
             |            "engageSubcontractors": true,
             |            "correspondenceName": "Thierry Henry",
             |            "correspondenceContactDetails": {
             |                "phoneNumber": "1234999999",
             |                "mobileNumber": "4358475999",
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

      response.status mustBe 200
      response.json mustBe Json.toJson("testAckRef")


      val reg = await(repository.retrieveRegistration(regId))

      reg.get.status mustBe PAYEStatus.submitted
      reg.get.fullSubmissionTimestamp.nonEmpty mustBe true
    }

    "return a 200 status with an ackRef when DES returns a 409" in new Setup {
      setupAuthMocksToReturn(authoriseData)

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

      await(repository.updateRegistration(submission))
      await(client(s"/test-only/feature-flag/desServiceFeature/true").get())

      val response = await(client(s"/$regId/submit-registration").put(""))

      verify(postRequestedFor(urlEqualTo("/business-registration/pay-as-you-earn"))
        .withHeader("Environment", matching("test-environment"))
        .withHeader("Authorization", matching("Bearer testAuthToken"))
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
             |        "completionCapacity": "Director",
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
             |                "phoneNumber": "0123459999",
             |                "mobileNumber": "5432109999",
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
             |            "dateOfFirstEXBForEmployees": "${SystemDate.getSystemDate.toLocalDate}",
             |            "numberOfEmployeesExpectedThisYear": "1",
             |            "engageSubcontractors": true,
             |            "correspondenceName": "Thierry Henry",
             |            "correspondenceContactDetails": {
             |                "phoneNumber": "1234999999",
             |                "mobileNumber": "4358475999",
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

      response.status mustBe 200
      response.json mustBe Json.toJson("testAckRef")

      val reg = await(repository.retrieveRegistration(regId))
      reg mustBe Some(processedSubmission.copy(lastUpdate = reg.get.lastUpdate, partialSubmissionTimestamp = reg.get.partialSubmissionTimestamp, lastAction = reg.get.lastAction))

      val regLastUpdate = mockDateHelper.getDateFromTimestamp(reg.get.lastUpdate)
      val submissionLastUpdate = mockDateHelper.getDateFromTimestamp(submission.lastUpdate)

      regLastUpdate.isAfter(submissionLastUpdate) mustBe true
      reg.get.partialSubmissionTimestamp.nonEmpty mustBe true
    }

    "return a 204 status when Incorporation was rejected at PAYE Submission" in new Setup {
      setupAuthMocksToReturn(authoriseData)

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 200, incorpUpdate(rejected))

      await(repository.updateRegistration(submission))
      await(repository.collection.countDocuments().toFuture()) mustBe 1

      val response = await(client(s"/$regId/submit-registration").put(""))
      response.status mustBe 204

      val reg = await(repository.retrieveRegistration(regId))
      reg mustBe None
    }

    "return a 502 status when DES returns a 499" in new Setup {
      setupAuthMocksToReturn(authoriseData)

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(499)
        )
      )

      stubBusinessProfile()

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      await(repository.updateRegistration(submission))
      await(client(s"/test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"/$regId/submit-registration").put("").futureValue
      response.status mustBe 502

      await(repository.retrieveRegistration(regId)) mustBe Some(submission)
    }

    "return a 502 status when DES returns a 5xx" in new Setup {
      setupAuthMocksToReturn(authoriseData)

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(533)
        )
      )

      stubBusinessProfile()

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      await(repository.updateRegistration(submission))
      await(client(s"/test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"/$regId/submit-registration").put("").futureValue
      response.status mustBe 502

      await(repository.retrieveRegistration(regId)) mustBe Some(submission)
    }
    "return a 503 status when DES returns a 429" in new Setup {
      setupAuthMocksToReturn(authoriseData)

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(429)
        )
      )

      stubBusinessProfile()

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      await(repository.updateRegistration(submission))
      await(client(s"/test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"/$regId/submit-registration").put("").futureValue
      response.status mustBe 503

      await(repository.retrieveRegistration(regId)) mustBe Some(submission)
    }
    "return a 400 status when DES returns a 4xx (apart from 429)" in new Setup {
      setupAuthMocksToReturn(authoriseData)

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(433)
        )
      )

      stubBusinessProfile()

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      await(repository.updateRegistration(submission))
      await(client(s"/test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"/$regId/submit-registration").put("").futureValue
      response.status mustBe 400

      await(repository.retrieveRegistration(regId)) mustBe Some(submission)
    }

    "return a 500 status when registration has already been cleared post-submission in mongo" in new Setup {
      setupAuthMocksToReturn(authoriseData)

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      await(repository.updateRegistration(processedSubmission))
      await(client(s"/test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"/$regId/submit-registration").put("").futureValue
      response.status mustBe 500

      await(repository.retrieveRegistration(regId)) mustBe Some(processedSubmission)
    }

    "return a 400 when in working hours" in new Setup {
      setupAuthMocksToReturn(authoriseData)
      await(client(s"/test-only/feature-flag/system-date/2018-01-01T12:00:00Z").get())

      val regime = "paye"
      val subscriber = "SCRS"

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(400)
        )
      )

      stubBusinessProfile()

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      val extraDirectorList = submission.directors :+ Director(
        name = Name(forename = Some("Malcolm"), surname = Some("Test"), otherForenames = Some("Testing"), title = Some("Mr")),
        nino = None
      )
      await(repository.updateRegistration(submission.copy(directors = extraDirectorList)))
      await(client(s"/test-only/feature-flag/desServiceFeature/true").get())

      val response = client(s"/$regId/submit-registration").put("").futureValue

      verify(postRequestedFor(urlEqualTo("/business-registration/pay-as-you-earn"))
        .withHeader("Environment", matching("test-environment"))
        .withHeader("Authorization", matching("Bearer testAuthToken"))
        .withRequestBody(equalToJson(Json.parse(
          s"""
             |{
             | "acknowledgementReference": "testAckRef",
             |    "metaData": {
             |        "businessType": "Limited company",
             |        "sessionID": "session-12345",
             |        "credentialID": "$credId",
             |        "language": "en",
             |        "formCreationTimestamp": "$timestamp",
             |        "submissionFromAgent": false,
             |        "completionCapacity": "Director",
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
             |                "phoneNumber": "0123459999",
             |                "mobileNumber": "5432109999",
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
             |            "dateOfFirstEXBForEmployees": "${SystemDate.getSystemDate.toLocalDate}",
             |            "numberOfEmployeesExpectedThisYear": "1",
             |            "engageSubcontractors": true,
             |            "correspondenceName": "Thierry Henry",
             |            "correspondenceContactDetails": {
             |                "phoneNumber": "1234999999",
             |                "mobileNumber": "4358475999",
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
      response.status mustBe 400
      await(client(s"/test-only/feature-flag/system-date/time-clear").get())

    }

    "return a 400 when out of working hours" in new Setup {
      setupAuthMocksToReturn(authoriseData)
      await(client(s"/test-only/feature-flag/system-date/2018-01-01T20:00:00Z").get())

      val regime = "paye"
      val subscriber = "SCRS"

      stubFor(post(urlMatching("/business-registration/pay-as-you-earn"))
        .willReturn(
          aResponse().
            withStatus(400)
        )
      )

      stubBusinessProfile()

      stubPost(s"/incorporation-information/subscribe/$transactionID/regime/$regime/subscriber/$subscriber", 202, "")

      val extraDirectorList = submission.directors :+ Director(
        name = Name(forename = Some("Malcolm"), surname = Some("Test"), otherForenames = Some("Testing"), title = Some("Mr")),
        nino = None
      )
      await(repository.updateRegistration(submission.copy(directors = extraDirectorList)))
      await(client(s"/test-only/feature-flag/desServiceFeature/true").get())


      val response = client(s"/$regId/submit-registration").put("").futureValue

      verify(postRequestedFor(urlEqualTo("/business-registration/pay-as-you-earn"))
        .withHeader("Environment", matching("test-environment"))
        .withHeader("Authorization", matching("Bearer testAuthToken"))
        .withRequestBody(equalToJson(Json.parse(
          s"""
             |{
             | "acknowledgementReference": "testAckRef",
             |    "metaData": {
             |        "businessType": "Limited company",
             |        "sessionID": "session-12345",
             |        "credentialID": "$credId",
             |        "language": "en",
             |        "formCreationTimestamp": "$timestamp",
             |        "submissionFromAgent": false,
             |        "completionCapacity": "Director",
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
             |                "phoneNumber": "0123459999",
             |                "mobileNumber": "5432109999",
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
             |            "dateOfFirstEXBForEmployees": "${SystemDate.getSystemDate.toLocalDate}",
             |            "numberOfEmployeesExpectedThisYear": "1",
             |            "engageSubcontractors": true,
             |            "correspondenceName": "Thierry Henry",
             |            "correspondenceContactDetails": {
             |                "phoneNumber": "1234999999",
             |                "mobileNumber": "4358475999",
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
      response.status mustBe 400
      await(client(s"/test-only/feature-flag/system-date/time-clear").get())
    }
  }

  "return a 200 with an ack ref when a full DES submission completes successfully with an EmploymentInfo data block" in new Setup {
    setupAuthMocksToReturn(authoriseData)

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

    val submission = PAYERegistration(
      regId,
      transactionID,
      intId,
      Some("testAckRef"),
      None,
      None,
      timestamp,
      PAYEStatus.draft,
      Some("Director"),
      Some(
        CompanyDetails(
          "téštÇômpæñÿÑámë[]{}#«»œßøÆŒ",
          Some("test"),
          Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), None, Some("roAuditRef")),
          Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK"), Some("ppobAuditRef")),
          DigitalContactDetails(Some("test@email.com"), Some("0123459999"), Some("5432109999"))
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
              Some("1234999999"),
              Some("4358475999")
            )
          ),
          correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), None, Some("UK"), Some("correspondenceAuditRef"))
        )
      ),
      Seq(
        SICCode(code = None, description = Some("consulting"))
      ),
      lastUpdate,
      partialSubmissionTimestamp = None,
      fullSubmissionTimestamp = None,
      acknowledgedTimestamp = None,
      lastAction = Some(dt),
      employmentInfo =  Some(
        EmploymentInfo(
          employees = Employing.willEmployNextYear,
          companyPension = Some(true),
          construction = true,
          subcontractors = true,
          firstPaymentDate = LocalDate.of(2016, 12, 20)
        )
      )
    )

    await(repository.updateRegistration(submission))
    await(client(s"/test-only/feature-flag/desServiceFeature/true").get())

    val response = await(client(s"/$regId/submit-registration").put(""))

    verify(postRequestedFor(urlEqualTo("/business-registration/pay-as-you-earn"))
      .withHeader("Environment", matching("test-environment"))
      .withHeader("Authorization", matching("Bearer testAuthToken"))
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
           |        "completionCapacity": "Director",
           |        "declareAccurateAndComplete": true
           |    },
           |    "payAsYouEarn": {
           |        "limitedCompany": {
           |            "companyUTR": "testCtUtr",
           |            "crn": "OC123456",
           |            "companiesHouseCompanyName": "testCompaenyNameoessoAEOE",
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
           |                "phoneNumber": "0123459999",
           |                "mobileNumber": "5432109999",
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
           |                "phoneNumber": "1234999999",
           |                "mobileNumber": "4358475999",
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

    response.status mustBe 200
    response.json mustBe Json.toJson("testAckRef")


    val reg = await(repository.retrieveRegistration(regId))

    reg.get.status mustBe PAYEStatus.submitted
    reg.get.fullSubmissionTimestamp.nonEmpty mustBe true
  }
}
