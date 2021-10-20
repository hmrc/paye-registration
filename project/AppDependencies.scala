
import sbt._

private object AppDependencies {
  def apply() = MainDependencies() ++ UnitTestDependencies() ++ IntegrationTestDependencies()
}

object MainDependencies {
  private val bootstrapVersion = "4.0.0"
  private val domainVersion = "5.10.0-play-27"
  private val mongoLockVersion = "6.24.0-play-27"
  private val simpleReactivemongoVersion = "7.31.0-play-27"

  def apply() = Seq(
    "com.typesafe.play" %% "play-json-joda" % "2.7.4",
    "com.enragedginger" %% "akka-quartz-scheduler" % "1.8.2-akka-2.6.x",
    "uk.gov.hmrc" %% "bootstrap-backend-play-27" % bootstrapVersion,
    "uk.gov.hmrc" %% "domain" % domainVersion,
    "uk.gov.hmrc" %% "mongo-lock" % mongoLockVersion,
    "uk.gov.hmrc" %% "simple-reactivemongo" % simpleReactivemongoVersion
  )
}

trait TestDependencies {
  val scalaTestVersion = "3.0.8"
  val scalaTestPlusVersion = "4.0.0"
  val mockitoCoreVersion = "2.13.0"
  val wireMockVersion = "2.27.2"
  val reactiveTestVersion = "5.0.0-play-27"

  val scope: Configuration
  val test: Seq[ModuleID]

  lazy val commonTestDependencies = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play" % scalaTestPlusVersion % scope,
    "uk.gov.hmrc" %% "reactivemongo-test" % reactiveTestVersion % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope
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
