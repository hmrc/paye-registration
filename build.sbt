
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import TestPhases.oneForkedJvmPerTest

val appName = "paye-registration"

lazy val scoverageSettings = Seq(
  ScoverageKeys.coverageExcludedPackages  := "<empty>;Reverse.*;model.*;config.*;.*(AuthService|BuildInfo|Routes).*",
  ScoverageKeys.coverageMinimum           := 80,
  ScoverageKeys.coverageFailOnMinimum     := false,
  ScoverageKeys.coverageHighlighting      := true
)

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala,SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin): _*)
  .settings(scalaSettings: _*)
  .settings(scoverageSettings : _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(PlayKeys.playDefaultPort := 9873)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    scalaVersion                                        := "2.11.11",
    libraryDependencies                                 ++= AppDependencies(),
    retrieveManaged                                     := true,
    parallelExecution in IntegrationTest                := false,
    resolvers                                           += Resolver.bintrayRepo("hmrc", "releases"),
    resolvers                                           += Resolver.jcenterRepo,
    evictionWarningOptions in update                    := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    routesGenerator                                     := InjectedRoutesGenerator,
    Keys.fork in IntegrationTest                        := false,
    unmanagedSourceDirectories in IntegrationTest       := (baseDirectory in IntegrationTest)(base => Seq(base / "it")).value,
    testGrouping in IntegrationTest                     := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    addTestReportOption(IntegrationTest, "int-test-reports")
  )