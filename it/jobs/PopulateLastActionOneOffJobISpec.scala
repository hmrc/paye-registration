package jobs

import java.time.{ZoneId, LocalDateTime, ZonedDateTime}

import com.google.inject.name.Names
import enums.PAYEStatus
import helpers.DateHelper
import itutil.{WiremockHelper, IntegrationSpecBase}
import models.PAYERegistration
import play.api.{Configuration, Application}
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.RegistrationMongo
import services.MetricsService
import uk.gov.hmrc.play.scheduling.ScheduledJob
import scala.concurrent.ExecutionContext.Implicits._

class PopulateLastActionOneOffJobISpec extends IntegrationSpecBase {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "Test.auditing.consumer.baseUri.host" -> s"$mockHost",
    "Test.auditing.consumer.baseUri.port" -> s"$mockPort"
  )

  override def beforeEach() = new Setup {
    resetWiremock()
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  lazy val reactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]
  lazy val sConfig = app.injector.instanceOf[Configuration]

  def lookupJob(name: String): ScheduledJob = {
    val qualifier = Some(QualifierInstance(Names.named(name)))
    val key = BindingKey[ScheduledJob](classOf[ScheduledJob], qualifier)
    app.injector.instanceOf[ScheduledJob](key)
  }

  val timestamp = ZonedDateTime.of(LocalDateTime.of(2017, 3, 3, 12, 30, 0, 0), ZoneId.of("Z"))
  val timestampString = "2017-03-03T12:30:00Z"

  class Setup {
    lazy val mockMetrics = app.injector.instanceOf[MetricsService]
    lazy val mockDateHelper = new DateHelper{ override def getTimestamp = timestamp }
    val mongo = new RegistrationMongo(mockMetrics, mockDateHelper, reactiveMongoComponent, sConfig)
    val repository = mongo.store
  }


  def reg(regId: String, lastAction: Option[ZonedDateTime], lastUpdate: String) = PAYERegistration(
    registrationID = regId,
    transactionID = s"trans$regId",
    internalID = s"Int-xxx",
    acknowledgementReference = None,
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = timestampString,
    eligibility = None,
    status = PAYEStatus.draft,
    completionCapacity = Some("Director"),
    companyDetails = None,
    directors = Seq.empty,
    payeContact = None,
    employment = None,
    sicCodes = Seq.empty,
    lastUpdate = lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = lastAction
  )

  "Populate Last Action Job" should {
    "take no action when job is disabled" in new Setup {
      setupFeatures(lastActionJob = false)

      val job = lookupJob("populate-last-action-one-off-job")
      val res = await(job.execute)
      res shouldBe job.Result("Populate last action Feature is turned off")
    }

    "update lastAction" when {
      "there are no lastAction timestamps" in new Setup {

        await(repository.upsertRegTestOnly(reg("12345", None, "2017-01-01T18:03:45Z")))
        await(repository.upsertRegTestOnly(reg("22345", None, "2017-05-05T18:03:48Z")))
        setupFeatures(lastActionJob = true)
        val job = lookupJob("populate-last-action-one-off-job")
        val res = await(job.execute)

        res shouldBe job.Result("populate-last-action-one-off-job: updated 2 documents successfully with 0 failures")
        await(repository.retrieveRegistration("12345")).flatMap(_.lastAction) shouldBe Some(
          ZonedDateTime.of(LocalDateTime.of(2017, 1, 1, 18, 3, 45), ZoneId.of("Z"))
        )
        await(repository.retrieveRegistration("22345")).flatMap(_.lastAction) shouldBe Some(
          ZonedDateTime.of(LocalDateTime.of(2017, 5, 5, 18, 3, 48), ZoneId.of("Z"))
        )
      }
      "there are already some lastAction timestamps" in new Setup {

        val lAction = ZonedDateTime.of(LocalDateTime.of(2016, 2, 19, 8, 40, 32), ZoneId.of("Z"))
        await(repository.upsertRegTestOnly(reg("12345", Some(lAction), "2017-01-01T18:03:45Z")))
        await(repository.upsertRegTestOnly(reg("22345", None, "2017-05-05T18:03:48Z")))
        setupFeatures(lastActionJob = true)
        val job = lookupJob("populate-last-action-one-off-job")
        val res = await(job.execute)

        res shouldBe job.Result("populate-last-action-one-off-job: updated 1 documents successfully with 0 failures")
        await(repository.retrieveRegistration("12345")).flatMap(_.lastAction) shouldBe Some(
          lAction
        )
        await(repository.retrieveRegistration("22345")).flatMap(_.lastAction) shouldBe Some(
          ZonedDateTime.of(LocalDateTime.of(2017, 5, 5, 18, 3, 48), ZoneId.of("Z"))
        )
      }
    }
  }

}
