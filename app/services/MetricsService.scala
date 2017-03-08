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

import javax.inject.{Inject, Singleton}

import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics

@Singleton
class MetricsService @Inject()(injMetrics: Metrics) extends MetricsSrv {
  override val mongoResponseTimer = injMetrics.defaultRegistry.timer("mongo-call-timer")
}

trait MetricsSrv {
  val mongoResponseTimer: Timer
}
