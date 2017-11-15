/*
 * Copyright 2017 HM Revenue & Customs
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

import javax.inject.{Inject, Provider, Singleton}

import com.codahale.metrics.{Gauge, Timer}
import com.kenshoo.play.metrics.{Metrics, MetricsDisabledException}
import play.api.Logger
import repositories.{RegistrationMongo, RegistrationMongoRepository}

import scala.concurrent.{ExecutionContext, Future}

class MetricsService @Inject()(injRegRepo: RegistrationMongo,
                               val metrics: Metrics) extends MetricsSrv {

  override lazy val regRepo = injRegRepo.store
  override val mongoResponseTimer = metrics.defaultRegistry.timer("mongo-call-timer")
}

trait MetricsSrv {
  val mongoResponseTimer: Timer
  protected val metrics: Metrics
  protected val regRepo: RegistrationMongoRepository

  def updateDocumentMetrics()(implicit ec: ExecutionContext): Future[Map[String, Int]] = {
    regRepo.getRegistrationStats() map {
      stats => {
        for( (status, count) <- stats ) {
          recordStatusCountStat(status, count)
        }
        stats
      }
    }
  }

  private def recordStatusCountStat(status: String, count: Int) = {
    val metricName = s"status-count-stat.$status"
    try {
      val gauge = new Gauge[Int] {
        val getValue: Int = count
      }
      metrics.defaultRegistry.remove(metricName)
      metrics.defaultRegistry.register(metricName, gauge)
    } catch {
      case ex: MetricsDisabledException => {
        Logger.warn(s"[MetricsService] [recordStatusCountStat] Metrics disabled - $metricName -> $count")
      }
    }
  }
}
