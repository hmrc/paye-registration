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

import enums.PAYEStatus
import helpers.DateHelper
import itutil.{IntegrationSpecBase, WiremockHelper}
import models._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.{RegistrationMongo, RegistrationMongoRepository, SequenceMongo, SequenceMongoRepository}
import services.MetricsService

import scala.concurrent.ExecutionContext.Implicits.global

class RepositoryControllerISpec extends IntegrationSpecBase {

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

  lazy val reactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]

  private def client(path: String) = ws.url(s"http://localhost:$port/paye-registration/$path")
    .withFollowRedirects(false)
    .withHeaders(("X-Session-ID","session-12345"))

  val regId = "12345"
  val transactionID = "NN1234"
  val intId = "Int-xxx"
  val timestamp = "2017-01-01T00:00:00"
  val lastUpdate = "2017-05-09T07:58:35Z"

  class Setup {
    lazy val mockMetrics = app.injector.instanceOf[MetricsService]
     val mockDateHelper = new DateHelper {
      override def getTimestampString: String = timestamp
     }
    val mongo = new RegistrationMongo(mockMetrics, mockDateHelper, reactiveMongoComponent)
    val sequenceMongo = new SequenceMongo(reactiveMongoComponent)
    val repository: RegistrationMongoRepository = mongo.store
    val sequenceRepository: SequenceMongoRepository = sequenceMongo.store
    await(repository.drop)
    await(repository.ensureIndexes)
    await(sequenceRepository.drop)
    await(sequenceRepository.ensureIndexes)
  }

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
    ),
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = None
  )

  "deletePAYERegistration" should {

    "return a NotFound trying deleting a non existing document" in new Setup {
      setupSimpleAuthMocks()

      val response = client(s"invalidRegId/delete-in-progress").delete().futureValue
      response.status shouldBe 404
    }

    "return a PreconditionFailed response if the document status is not 'rejected'" in new Setup {
      setupSimpleAuthMocks()

      val rejected = submission.copy(status = PAYEStatus.rejected)

      await(repository.insert(rejected))

      val response = client(s"$regId/delete-in-progress").delete().futureValue
      response.status shouldBe 412

      await(repository.retrieveRegistration(rejected.registrationID)) shouldBe Some(rejected)
    }

    "return an OK after deleting an invalid document" in new Setup {
      setupSimpleAuthMocks()

      await(repository.insert(submission.copy(status = PAYEStatus.invalid)))

      val response = await(client(s"$regId/delete-in-progress").delete())
      response.status shouldBe 200

      await(repository.retrieveRegistration(submission.registrationID)) shouldBe None
    }

    "return an OK after deleting a draft document" in new Setup {
      setupSimpleAuthMocks()

      await(repository.insert(submission.copy(status = PAYEStatus.draft)))

      val response = await(client(s"$regId/delete-in-progress").delete())
      response.status shouldBe 200

      await(repository.retrieveRegistration(submission.registrationID)) shouldBe None
    }

  }

}
