
import sbt._

private object AppDependencies {
  def apply() = MainDependencies() ++ UnitTestDependencies() ++ IntegrationTestDependencies()
}

object MainDependencies {
  private val bootstrapVersion = "1.8.0"
  private val domainVersion = "5.6.0-play-26"
  private val mongoLockVersion = "6.21.0-play-26"
  private val playSchedulingVersion = "7.4.0-play-26"
  private val simpleReactivemongoVersion = "7.27.0-play-26"
  private val authClientVersion = "3.0.0-play-26"

  def apply() = Seq(
    "com.typesafe.play" %% "play-json-joda" % "2.6.10",
    "com.enragedginger" %% "akka-quartz-scheduler" % "1.8.0-akka-2.5.x",
    "uk.gov.hmrc" %% "bootstrap-play-26" % bootstrapVersion,
    "uk.gov.hmrc" %% "domain" % domainVersion,
    "uk.gov.hmrc" %% "mongo-lock" % mongoLockVersion,
    "uk.gov.hmrc" %% "simple-reactivemongo" % simpleReactivemongoVersion,
    "uk.gov.hmrc" %% "play-scheduling" % playSchedulingVersion,
    "uk.gov.hmrc" %% "auth-client" % authClientVersion
  )
}

trait TestDependencies {
  val scalaTestVersion = "3.0.8"
  val scalaTestPlusVersion = "3.1.3"
  val mockitoCoreVersion = "2.13.0"
  val wireMockVersion = "2.26.3"
  val reactiveTestVersion = "4.19.0-play-26"

  val scope: Configuration
  val test: Seq[ModuleID]

  lazy val commonTestDependencies = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play" % scalaTestPlusVersion % scope,
    "uk.gov.hmrc" %% "reactivemongo-test" % reactiveTestVersion % scope
  )

  def apply() = test
}

object UnitTestDependencies extends TestDependencies {
  val scope = Test
  val test = commonTestDependencies ++ Seq(
    "org.mockito" % "mockito-core" % mockitoCoreVersion
  )
}

object IntegrationTestDependencies extends TestDependencies {
  val scope = IntegrationTest
  val test = commonTestDependencies ++ Seq(
    "com.github.tomakehurst" % "wiremock-jre8" % wireMockVersion % scope
  )
}
