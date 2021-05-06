/*
 * Copyright 2021 HM Revenue & Customs
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

package jobs

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}

import auth.CryptoSCRS
import com.google.inject.name.Names
import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import enums.PAYEStatus
import helpers.DateHelper
import itutil.{IntegrationSpecBase, WiremockHelper}
import models.PAYERegistration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.{Application, Configuration}
import play.api.test.Helpers._
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.RegistrationMongoRepository

import scala.concurrent.ExecutionContext.Implicits.global

class MetricsJobISpec extends IntegrationSpecBase {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
    "metrics.enabled" -> "true",
    "auditing.consumer.baseUri.host" -> mockHost,
    "auditing.consumer.baseUri.port" -> mockPort,
    "Test.auditing.consumer.baseUri.host" -> mockHost,
    "Test.auditing.consumer.baseUri.port" -> mockPort,
    "constants.maxStorageDays" -> 60
  )

  override def beforeEach() = new Setup(timestamp) {
    resetWiremock()
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  lazy val reactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]
  lazy val sConfig = app.injector.instanceOf[Configuration]
  lazy val appConfig = app.injector.instanceOf[AppConfig]

  def lookupJob(name: String): ScheduledJob = {
    val qualifier = Some(QualifierInstance(Names.named(name)))
    val key = BindingKey[ScheduledJob](classOf[ScheduledJob], qualifier)
    app.injector.instanceOf[ScheduledJob](key)
  }

  def reg(regId: String, lastAction: Option[ZonedDateTime], status: PAYEStatus.Value) = PAYERegistration(
    registrationID = regId,
    transactionID = s"trans$regId",
    internalID = s"Int-xxx",
    acknowledgementReference = None,
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = timestampString,
    status = status,
    completionCapacity = Some("Director"),
    companyDetails = None,
    directors = Seq.empty,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate = timestampString,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = lastAction,
    employmentInfo = None
  )

  val timestamp = ZonedDateTime.of(LocalDateTime.of(2017, 3, 3, 12, 30, 0, 0), ZoneId.of("Z"))
  val timestampString = "2017-03-03T12:30:00Z"

  class Setup(ts: ZonedDateTime) {
    lazy val mockMetrics = app.injector.instanceOf[Metrics]
    lazy val mockDateHelper = new DateHelper {
      override def getTimestamp: ZonedDateTime = ts
    }
    lazy val mockcryptoSCRS = app.injector.instanceOf[CryptoSCRS]
    val repository = new RegistrationMongoRepository(mockMetrics, mockDateHelper, reactiveMongoComponent, sConfig, mockcryptoSCRS)
    lazy val lockRepository = app.injector.instanceOf[LockRepositoryProvider].repo
  }

  "Metrics Job" should {
    "take no action when the job is disabled" in new Setup(timestamp) {
      setupFeatures(metricsJob = false)

      val job = lookupJob("metrics-job")
      val res = job.schedule

      res shouldBe false
      //      res shouldBe job.Result("Feature metrics-job is turned off")
    }

    "return what documents are currently in PAYE" in new Setup(timestamp) {
      setupFeatures(metricsJob = true)

      val dateTime = ZonedDateTime.of(LocalDateTime.of(2017, 3, 1, 12, 0), ZoneId.of("Z"))

      await(repository.upsertRegTestOnly(reg("223", Some(dateTime), PAYEStatus.draft)))
      await(repository.upsertRegTestOnly(reg("224", Some(dateTime), PAYEStatus.draft)))
      await(repository.upsertRegTestOnly(reg("225", Some(dateTime), PAYEStatus.cancelled)))

      val job = lookupJob("metrics-job")
      await(lockRepository.drop)
      val f = job.scheduledMessage.service.invoke.map(_.asInstanceOf[Either[Map[String, Int], LockResponse]])
      val res = await(f)

      val docMap = Left(Map("cancelled" -> 1, "draft" -> 2))

      res shouldBe docMap

    }
  }

}
