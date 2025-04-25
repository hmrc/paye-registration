
import sbt._

object AppDependencies {

  private val playVersion                 =  "-play-30"
  private val bootstrapVersion            =  "8.6.0"
  private val domainVersion               =  "8.3.0-play-28"
  private val scalaTestVersion            =  "3.2.18"
  private val scalaTestPlusPlayVersion    =  "7.0.1"
  private val wireMockVersion             =  "3.0.1"
  private val hmrcMongoVersion            =  "2.6.0"
  private val quartzSchedulerVersion      =  "1.2.0-pekko-1.0.x"
  private val flexmarkAllVersion          =  "0.64.8"
  private val taxYearVersion              =  "4.0.0"

  val compile = Seq(
    "uk.gov.hmrc"               %%  "tax-year"                        % taxYearVersion,
    "uk.gov.hmrc"               %% s"bootstrap-backend$playVersion"   % bootstrapVersion,
    "uk.gov.hmrc"               %%  "domain"                          % domainVersion,
    "uk.gov.hmrc.mongo"         %% s"hmrc-mongo$playVersion"          % hmrcMongoVersion,
    "io.github.samueleresca"    %% "pekko-quartz-scheduler"           % quartzSchedulerVersion
  )

  val test = Seq(
    "uk.gov.hmrc"               %% s"bootstrap-test$playVersion"      %  bootstrapVersion,
    "org.scalatest"             %%  "scalatest"                       %  scalaTestVersion,
    "org.scalatestplus.play"    %%  "scalatestplus-play"              %  scalaTestPlusPlayVersion,
    "com.vladsch.flexmark"      %   "flexmark-all"                    %  flexmarkAllVersion,
    "org.scalatestplus"         %%  "mockito-4-5"                     % "3.2.12.0"
  ).map(_ % Test)

  val it = Seq(
    "com.github.tomakehurst" % "wiremock-jre8-standalone" % wireMockVersion ,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-test$playVersion" % hmrcMongoVersion
  ).map( _ % Test)

  def apply() = compile ++ test
}
