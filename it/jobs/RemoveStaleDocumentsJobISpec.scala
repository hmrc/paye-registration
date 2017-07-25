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

import scala.concurrent.ExecutionContext.Implicits.global

class RemoveStaleDocumentsJobISpec extends IntegrationSpecBase {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "Test.auditing.consumer.baseUri.host" -> s"$mockHost",
    "Test.auditing.consumer.baseUri.port" -> s"$mockPort",
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

  def lookupJob(name: String): ScheduledJob = {
    val qualifier = Some(QualifierInstance(Names.named(name)))
    val key = BindingKey[ScheduledJob](classOf[ScheduledJob], qualifier)
    app.injector.instanceOf[ScheduledJob](key)
  }

  def reg(regId: String, lastAction: Option[ZonedDateTime]) = PAYERegistration(
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
    lastUpdate = timestampString,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = lastAction
  )

  val timestamp = ZonedDateTime.of(LocalDateTime.of(2017, 3, 3, 12, 30, 0, 0), ZoneId.of("Z"))
  val timestampString = "2017-03-03T12:30:00Z"

  class Setup(ts: ZonedDateTime) {
    lazy val mockMetrics = app.injector.instanceOf[MetricsService]
    lazy val mockDateHelper = new DateHelper{ override def getTimestamp: ZonedDateTime = ts }
    val mongo = new RegistrationMongo(mockMetrics, mockDateHelper, reactiveMongoComponent, sConfig)
    val repository = mongo.store
  }

  "Remove Stale Documents Job" should {
    "take no action when job is disabled" in new Setup(timestamp) {
      setupFeatures(removeStaleDocumentsJob = false)

      val job = lookupJob("remove-stale-documents-job")
      val res = await(job.execute)
      res shouldBe job.Result("Remove stale documents feature is turned off")
    }

    "remove documents older than a config specified length of time" in new Setup(timestamp) {
      val deleteDT = ZonedDateTime.of(LocalDateTime.of(2016, 12, 1, 12, 0), ZoneId.of("Z"))
      val keepDT = ZonedDateTime.of(LocalDateTime.of(2017, 3, 1, 12, 0), ZoneId.of("Z"))
      await(repository.upsertRegTestOnly(reg("123", Some(deleteDT))))
      await(repository.upsertRegTestOnly(reg("223", Some(keepDT))))

      setupFeatures(removeStaleDocumentsJob = true)
      val job = new RemoveStaleDocumentsJobImpl(mongo)
      val res = await(job.execute)

      res shouldBe job.Result("remove-stale-documents-job: Successfully removed 1 documents that were last updated before 2017-01-02T12:30Z")
      await(repository.retrieveRegistration("123")) shouldBe None
      await(repository.retrieveRegistration("223")) shouldBe Some(reg("223", Some(keepDT)))
    }
  }

}
