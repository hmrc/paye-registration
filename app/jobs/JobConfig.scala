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

package jobs

import org.joda.time.{Duration => JodaDuration}
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.duration.{FiniteDuration, Duration => ScalaDuration}

trait JobConfig {
  val servicesConfig: ServicesConfig
  val name: String

  lazy val LOCK_TIMEOUT        = s"$name.schedule.lockTimeout"

  lazy val lockTimeout : JodaDuration = {
    val dur = ScalaDuration.create(servicesConfig.getConfString(LOCK_TIMEOUT,
      throw new RuntimeException(s"Could not find config $LOCK_TIMEOUT")))
    JodaDuration.standardSeconds( FiniteDuration(dur.length, dur.unit).toSeconds )
  }
}
