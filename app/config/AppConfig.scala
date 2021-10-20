/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package config

import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject

class AppConfig @Inject()(servicesConfig: ServicesConfig) {

  lazy val businessRegUrl: String = servicesConfig.baseUrl("business-registration")

  lazy val compRegUrl: String = servicesConfig.baseUrl("company-registration")

  lazy val desUrl = servicesConfig.getConfString("des-service.url", "")
  lazy val desURI = servicesConfig.getConfString("des-service.uri", "")
  lazy val desTopUpURI = servicesConfig.getConfString("des-service.top-up-uri", "")
  lazy val desStubUrl = servicesConfig.baseUrl("des-stub")
  lazy val desStubURI = servicesConfig.getConfString("des-stub.uri", "")
  lazy val desStubTopUpURI = servicesConfig.getConfString("des-stub.top-up-uri", "")
  lazy val desUrlHeaderEnvironment: String = servicesConfig.getConfString("des-service.environment", throw new Exception("could not find config value for des-service.environment"))
  lazy val desUrlHeaderAuthorization: String = s"Bearer ${
    servicesConfig.getConfString("des-service.authorization-token",
      throw new Exception("could not find config value for des-service.authorization-token"))
  }"
  lazy val alertWorkingHours = servicesConfig.getConfString("alert-working-hours", throw new Exception("could not find config value for alert-working-hours"))

  lazy val incorporationInformationUri: String = servicesConfig.baseUrl("incorporation-information")
  lazy val payeRegUri: String = servicesConfig.baseUrl("paye-registration")

  def getLockTimeout(name: String): String = {
    val LOCK_TIMEOUT = s"$name.schedule.lockTimeout"

    servicesConfig.getConfString(LOCK_TIMEOUT, throw new RuntimeException(s"Could not find config $LOCK_TIMEOUT"))
  }

  lazy val metricsJobLockTimeout: Int = servicesConfig.getInt("schedules.metrics-job.lockTimeout")
  lazy val removeStaleDocumentsJobLockoutTimeout: Int = servicesConfig.getInt("schedules.remove-stale-documents-job.lockTimeout")

  lazy val payeRestartURL = servicesConfig.getString("api.payeRestartURL")
  lazy val payeCancelURL = servicesConfig.getString("api.payeCancelURL")
}
