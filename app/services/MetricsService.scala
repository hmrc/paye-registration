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

package services

import com.codahale.metrics.{Gauge, Timer}
import com.kenshoo.play.metrics.{Metrics, MetricsDisabledException}
import config.AppConfig
import jobs._
import org.joda.time.Duration
import play.api.Logging
import repositories.RegistrationMongoRepository
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class MetricsService @Inject()(val regRepo: RegistrationMongoRepository,
                               lockRepository: LockRepositoryProvider,
                               appConfig: AppConfig,
                               val metrics: Metrics) extends ScheduledService[Either[Map[String, Int], LockResponse]] with Logging {

  lazy val mongoResponseTimer: Timer = metrics.defaultRegistry.timer("mongo-call-timer")
  lazy val lock: LockKeeper = new LockKeeper() {
    override val lockId: String = "remove-stale-documents-job"
    override val forceLockReleaseAfter: Duration = Duration.standardSeconds(appConfig.metricsJobLockTimeout)
    override lazy val repo: LockRepository = lockRepository.repo
  }

  def invoke(implicit ec: ExecutionContext): Future[Either[Map[String, Int], LockResponse]] = {
    lock.tryLock(updateDocumentMetrics).map {
      case Some(res) =>
        logger.info("MetricsService acquired lock and returned results")
        Left(res)
      case None =>
        logger.info("MetricsService cant acquire lock")
        Right(MongoLocked)
    }.recover {
      case e => logger.error(s"Error running updateSubscriptionMetrics with message: ${e.getMessage}")
        Right(UnlockingFailed)
    }
  }

  def updateDocumentMetrics()(implicit ec: ExecutionContext): Future[Map[String, Int]] = {
    regRepo.getRegistrationStats() map {
      stats => {
        for ((status, count) <- stats) {
          recordStatusCountStat(status, count)
        }
        stats
      }
    }
  }

  private def recordStatusCountStat(status: String, count: Int) = {
    val metricName = s"status-count-stat.$status"
    try {
      val gauge: Gauge[Int] = new Gauge[Int] {
        val getValue: Int = count
      }
      metrics.defaultRegistry.remove(metricName)
      metrics.defaultRegistry.register(metricName, gauge)
    } catch {
      case _: MetricsDisabledException => {
        logger.warn(s"[MetricsService] [recordStatusCountStat] Metrics disabled - $metricName -> $count")
      }
    }
  }
}
