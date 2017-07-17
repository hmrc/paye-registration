package jobs

import java.time.{ZoneId, LocalDateTime, ZonedDateTime}

import com.google.inject.name.Names
import helpers.DateHelper
import itutil.{WiremockHelper, IntegrationSpecBase}
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.inject.guice.GuiceApplicationBuilder
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

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  def lookupJob(name: String): ScheduledJob = {
    val qualifier = Some(QualifierInstance(Names.named(name)))
    val key = BindingKey[ScheduledJob](classOf[ScheduledJob], qualifier)
    app.injector.instanceOf[ScheduledJob](key)
  }

  val timestamp = ZonedDateTime.of(LocalDateTime.of(2017, 3, 3, 12, 30, 0, 0), ZoneId.of("Z"))

  class Setup {
    lazy val mockMetrics = app.injector.instanceOf[MetricsService]
    lazy val mockDateHelper = new DateHelper{ override def getTimestamp = timestamp }
    val mongo = new RegistrationMongo(mockMetrics, mockDateHelper)
    val repository = mongo.store
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  "Populate Last Action Job" should {
    "take no action when job is disabled" in new Setup {
      setupFeatures(lastActionJob = false)

      val job = lookupJob("populate-last-action-one-off-job")
      val res = await(job.execute)
      res shouldBe job.Result("Populate last action Feature is turned off")
    }
  }

}
