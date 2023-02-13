
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, integrationTestSettings, scalaSettings}
import SbtBobbyPlugin.BobbyKeys.bobbyRulesURL
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

val appName = "paye-registration"

lazy val scoverageSettings = Seq(
  ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;model.*;config.*;.*(AuthService|BuildInfo|Routes).*",
  ScoverageKeys.coverageMinimumStmtTotal := 90,
  ScoverageKeys.coverageFailOnMinimum := true,
  ScoverageKeys.coverageHighlighting := true
)

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtDistributablesPlugin): _*)
  .settings(scalaSettings: _*)
  .settings(scoverageSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(integrationTestSettings(): _*)
  .settings(PlayKeys.playDefaultPort := 9873)
  .configs(IntegrationTest)
  .settings(majorVersion := 1)
  .settings(bobbyRulesURL := Some(new URL("https://webstore.tax.service.gov.uk/bobby-config/deprecated-dependencies.json")))
  .settings(
    scalacOptions += "-Xlint:-unused",
    scalaVersion := "2.13.8",
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true,
    IntegrationTest / parallelExecution := false,
    resolvers += Resolver.jcenterRepo,
    routesGenerator := InjectedRoutesGenerator
  )