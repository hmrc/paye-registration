/*
 * Copyright 2024 HM Revenue & Customs
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

import org.apache.pekko.actor.ActorSystem
import jobs.SchedulingActor.MetricsJob
import play.api.Configuration
import services.MetricsService

import javax.inject.{Inject, Singleton}

@Singleton
class MetricsJobs @Inject()(metricsService: MetricsService,
                           val config: Configuration) extends ScheduledJob {
  val jobName = "metrics-job"
  val actorSystem = ActorSystem(jobName)
  val scheduledMessage = MetricsJob(metricsService)
  schedule
}
