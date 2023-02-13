
import sbt._

object AppDependencies {

  private val playVersion                 =  "-play-28"
  private val bootstrapVersion            =  "7.13.0"
  private val domainVersion               = s"8.1.0$playVersion"
  private val scalaTestVersion            =  "3.2.15"
  private val scalaTestPlusPlayVersion    =  "5.1.0"
  private val wireMockVersion             =  "2.35.0"
  private val hmrcMongoVersion            =  "0.74.0"
  private val quartzSchedulerVersion      =  "1.9.3-akka-2.6.x"
  private val flexmarkAllVersion          =  "0.64.0"
  private val taxYearVersion              =  "3.0.0"

  val compile = Seq(
    "uk.gov.hmrc"               %%  "tax-year"                        % taxYearVersion,
    "com.enragedginger"         %%  "akka-quartz-scheduler"           % quartzSchedulerVersion,
    "uk.gov.hmrc"               %% s"bootstrap-backend$playVersion"   % bootstrapVersion,
    "uk.gov.hmrc"               %%  "domain"                          % domainVersion,
    "uk.gov.hmrc.mongo"         %% s"hmrc-mongo$playVersion"          % hmrcMongoVersion
  )

  val test = Seq(
    "uk.gov.hmrc"               %% s"bootstrap-test$playVersion"      %  bootstrapVersion           % "test, it",
    "org.scalatest"             %%  "scalatest"                       %  scalaTestVersion           % "test, it",
    "org.scalatestplus.play"    %%  "scalatestplus-play"              %  scalaTestPlusPlayVersion   % "test, it",
    "com.vladsch.flexmark"      %   "flexmark-all"                    %  flexmarkAllVersion         % "test, it",
    "org.scalatestplus"         %%  "mockito-4-5"                     % "3.2.12.0"                  % "test",
    "com.github.tomakehurst"    %   "wiremock-jre8-standalone"        %  wireMockVersion            % "it",
    "uk.gov.hmrc.mongo"         %% s"hmrc-mongo-test$playVersion"     %  hmrcMongoVersion           % "it"
  )

  def apply() = compile ++ test
}
