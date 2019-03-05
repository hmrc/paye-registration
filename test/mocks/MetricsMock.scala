/*
 * Copyright 2019 HM Revenue & Customs
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

package mocks

import com.codahale.metrics.{Counter, Timer}
import com.kenshoo.play.metrics.Metrics
import helpers.PAYERegSpec
import repositories.RegistrationMongoRepository
import services.MetricsSrv
import uk.gov.hmrc.lock.LockKeeper

object MetricsMock extends MetricsSrv with PAYERegSpec {
  lazy val mockContext = mock[Timer.Context]
  val mockTimer = new Timer()
  val mockCounter = mock[Counter]
  val metrics = mock[Metrics]
  val regRepo = mock[RegistrationMongoRepository]
  val lock = mock[LockKeeper]

  override val mongoResponseTimer = mockTimer
}
