/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Named, Singleton}
import auth.Crypto
import com.typesafe.config.Config
import jobs.RetrieveRegInfoFromTxIdJob
import net.ceedubs.ficus.Ficus._
import play.api.{Application, Configuration, Logger, Play}
import repositories.{RegistrationMongo, RegistrationMongoRepository}
import uk.gov.hmrc.play.auth.controllers.AuthParamsControllerConfig
import uk.gov.hmrc.play.auth.microservice.filters.AuthorisationFilter
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.play.scheduling.{RunningOfScheduledJobs, ScheduledJob}
import uk.gov.hmrc.play.microservice.filters.{AuditFilter, LoggingFilter, MicroserviceFilterSupport}


object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport {
  override val auditConnector = MicroserviceAuditConnector
  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}

object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with RunMode with MicroserviceFilterSupport with RunningOfScheduledJobs {
  override lazy val scheduledJobs = Play.current.injector.instanceOf[Jobs].lookupJobs()
  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = None

  override def onStart(app : play.api.Application) : scala.Unit = {

    try {
      Crypto.crypto.encrypt(PlainText("foo"))
      Logger.info("Mongo encryption key is valid")
    } catch {
      case ex: Throwable => Logger.error("Invalid_Mongo_Encryption_Key", ex)
    }

    import scala.concurrent.ExecutionContext.Implicits.global
    val repo = Play.current.injector.instanceOf[RegistrationMongo]
    repo.store.getRegistrationStats() map {
      stats => Logger.info(s"[RegStats] ${stats}")
    }

    new RetrieveRegInfoFromTxIdJob(repo.store, Play.configuration(app)).logRegInfoFromTxId()

    super.onStart(app)
  }
}

trait JobsList {
  def lookupJobs(): Seq[ScheduledJob] = Seq()
}

@Singleton
class Jobs @Inject()(
                      @Named("remove-stale-documents-job") removeStaleDocsJob: ScheduledJob,
                      @Named("metrics-job") graphiteMetrics: ScheduledJob
                      ) extends JobsList {
  override def lookupJobs(): Seq[ScheduledJob] =
    Seq(
      removeStaleDocsJob,
      graphiteMetrics
    )
}
