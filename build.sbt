
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.{integrationTestSettings, defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

val appName = "paye-registration"

lazy val scoverageSettings = Seq(
  ScoverageKeys.coverageExcludedPackages  := "<empty>;Reverse.*;model.*;config.*;.*(AuthService|BuildInfo|Routes).*",
  ScoverageKeys.coverageMinimum           := 80,
  ScoverageKeys.coverageFailOnMinimum     := false,
  ScoverageKeys.coverageHighlighting      := true
)

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala,SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory): _*)
  .settings(scalaSettings: _*)
  .settings(scoverageSettings : _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(integrationTestSettings(): _*)
  .settings(PlayKeys.playDefaultPort := 9873)
  .configs(IntegrationTest)
  .settings( majorVersion := 1 )
  .settings(
    scalaVersion                                        := "2.11.11",
    libraryDependencies                                 ++= AppDependencies(),
    retrieveManaged                                     := true,
    parallelExecution in IntegrationTest                := false,
    resolvers                                           += Resolver.bintrayRepo("hmrc", "releases"),
    resolvers                                           += Resolver.jcenterRepo,
    evictionWarningOptions in update                    := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    routesGenerator                                     := InjectedRoutesGenerator
  )