
import sbt._

private object AppDependencies {
  def apply() = MainDependencies() ++ UnitTestDependencies() ++ IntegrationTestDependencies()
}

object MainDependencies {
  private val microserviceBootstrapVersion  = "8.3.0"
  private val playUrlBindersVersion         = "2.1.0"
  private val domainVersion                 = "5.2.0"
  private val playReactivemongoVersion      = "5.2.0"
  private val mongoLockVersion              = "4.1.0"
  private val playSchedulingVersion         = "4.1.0"
  private val authClientVersion             = "2.16.0-play-25"

  def apply() = Seq(
    "uk.gov.hmrc" %% "play-reactivemongo"     % playReactivemongoVersion,
    "uk.gov.hmrc" %% "microservice-bootstrap" % microserviceBootstrapVersion,
    "uk.gov.hmrc" %% "play-url-binders"       % playUrlBindersVersion,
    "uk.gov.hmrc" %% "domain"                 % domainVersion,
    "uk.gov.hmrc" %% "mongo-lock"             % mongoLockVersion,
    "uk.gov.hmrc" %% "play-scheduling"        % playSchedulingVersion,
    "uk.gov.hmrc" %% "auth-client"            % authClientVersion
  )
}

trait TestDependencies {
  val scalaTestVersion      = "3.0.1"
  val scalaTestPlusVersion  = "2.0.0"
  val hmrcTestVersion       = "3.1.0"
  val mocklitoCoreVersion   = "2.13.0"
  val wireMockVersion       = "2.6.0"
  val reactiveTestVersion   = "2.0.0"

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
