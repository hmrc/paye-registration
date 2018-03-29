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

package utils

import java.time.LocalTime

import uk.gov.hmrc.play.test.UnitSpec

class WorkingHoursGuardSpec extends UnitSpec {

  trait Setup extends WorkingHoursGuard {
    override val alertWorkingHours: String = "08:00:00_18:00:00"
  }

  "isWorkingHours" should {
    "return true" when {
      Seq(
        (1, 8, 0),
        (1, 8, 1),
        (5, 17, 59),
        (5, 8, 0),
        (3, 12, 30)
      ) foreach { case (day, hour, minute) =>
        f"today is day $day and the time is $hour%02d:$minute%02d" in new Setup {
          override private[utils] val localDayNow = day
          override private[utils] val localTimeNow = LocalTime.of(hour, minute)

          isInWorkingDaysAndHours shouldBe true
        }
      }
    }

    "return false" when {
      Seq(
        (1, 7, 0),
        (7, 8, 1),
        (6, 18, 0),
        (5, 18, 0),
        (1, 18, 0),
        (5, 18, 1)
      ) foreach { case (day, hour, minute) =>
        f"today is day $day and the time is $hour%02d:$minute%02d" in new Setup {
          override private[utils] val localDayNow = day
          override private[utils] val localTimeNow = LocalTime.of(hour, minute)

          isInWorkingDaysAndHours shouldBe false
        }
      }
    }
  }

}
