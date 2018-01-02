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

package jobs

import javax.inject.{Inject, Singleton}

import org.joda.time.Duration
import play.api.{Logger, Play}
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DefaultDB
import services.MetricsService
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}
import uk.gov.hmrc.play.scheduling.ExclusiveScheduledJob
import utils.PAYEFeatureSwitches

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MetricsJobImpl @Inject()(val metricsService: MetricsService
                               ) extends MetricsJob {
  val name = "metrics-job"
  lazy val db: () => DefaultDB = new MongoDbConnection{}.db
}

trait MetricsJob extends ExclusiveScheduledJob with JobConfig with JobHelper {

  val metricsService: MetricsService

  override def executeInMutex(implicit ec: ExecutionContext): Future[Result] = {
    ifFeatureEnabled(PAYEFeatureSwitches.graphiteMetrics) {
      whenLockAcquired {
        metricsService.updateDocumentMetrics() map { result =>
          val message = s"Feature is turned on - result = Updated document stats - $result"
          Logger.info(message)
          Result(message)
        }
      }
    }
  }
}
