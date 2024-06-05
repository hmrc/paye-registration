
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, scalaSettings}
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion
import uk.gov.hmrc.DefaultBuildSettings

val appName = "paye-registration"

lazy val scoverageSettings = Seq(
  ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;model.*;config.*;.*(AuthService|BuildInfo|Routes).*",
  ScoverageKeys.coverageMinimumStmtTotal := 90,
  ScoverageKeys.coverageFailOnMinimum := true,
  ScoverageKeys.coverageHighlighting := true
)

ThisBuild / majorVersion := 1
ThisBuild / scalaVersion := "2.13.12"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .settings(scalaSettings *)
  .settings(scoverageSettings *)
  .settings(defaultSettings() *)
  .settings(
    PlayKeys.playDefaultPort := 9873,
    scalacOptions += "-Xlint:-unused",
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true,
    resolvers += Resolver.jcenterRepo,
    routesGenerator := InjectedRoutesGenerator
  )

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(
    libraryDependencies ++= AppDependencies.it)

