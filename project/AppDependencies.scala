
import sbt._

private object AppDependencies {
  def apply() = MainDependencies() ++ UnitTestDependencies() ++ IntegrationTestDependencies()
}

object MainDependencies {
  private val bootstrapVersion              = "4.9.0"
  private val domainVersion                 = "5.3.0"
  private val mongoLockVersion              = "6.10.0-play-25"
  private val playSchedulingVersion         = "5.4.0"
  private val simpleReactivemongoVersion    = "7.12.0-play-25"
  private val authClientVersion             = "2.20.0-play-25"

  def apply() = Seq(
    "com.enragedginger" %% "akka-quartz-scheduler" % "1.8.0-akka-2.5.x",
    "uk.gov.hmrc" %% "bootstrap-play-25"      % bootstrapVersion,
    "uk.gov.hmrc" %% "domain"                 % domainVersion,
    "uk.gov.hmrc" %% "mongo-lock"             % mongoLockVersion,
    "uk.gov.hmrc" %% "simple-reactivemongo"   % simpleReactivemongoVersion,
    "uk.gov.hmrc" %% "play-scheduling"        % playSchedulingVersion,
    "uk.gov.hmrc" %% "auth-client"            % authClientVersion
  )
}

trait TestDependencies {
  val scalaTestVersion      = "3.0.0"
  val scalaTestPlusVersion  = "2.0.0"
  val hmrcTestVersion       = "3.5.0-play-25"
  val mocklitoCoreVersion   = "2.13.0"
  val wireMockVersion       = "2.21.0"
  val reactiveTestVersion   = "4.7.0-play-25"

  val scope: Configuration
  val test : Seq[ModuleID]

  lazy val commonTestDependencies =  Seq(
    "uk.gov.hmrc"             %% "hmrctest"           % hmrcTestVersion       % scope,
    "org.scalatestplus.play"  %% "scalatestplus-play" % scalaTestPlusVersion  % scope,
    "uk.gov.hmrc"             %% "reactivemongo-test" % reactiveTestVersion   % scope
  )

  def apply() = test
}

object UnitTestDependencies extends TestDependencies {
  val scope = Test
  val test = commonTestDependencies ++ Seq(
    "org.mockito" % "mockito-core" % mocklitoCoreVersion
  )
}

object IntegrationTestDependencies extends TestDependencies {
  val scope = IntegrationTest
  val test = commonTestDependencies ++ Seq(
    "com.github.tomakehurst" % "wiremock" % wireMockVersion % scope
  )
}
