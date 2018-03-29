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

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalTime}

trait WorkingHoursGuard {
  val alertWorkingHours: String

  private[utils] def localTimeNow: LocalTime = LocalTime.now
  private[utils] def localDayNow: Int = LocalDate.now().getDayOfWeek.getValue

  private def isBetweenLoggingTimes: Boolean = {
    implicit val format: String => LocalTime = LocalTime.parse(_: String, DateTimeFormatter.ofPattern("HH:mm:ss"))
    val Array(start, end) = alertWorkingHours.split("_")
    //TODO: Refactor code to make it simple
    ((start isBefore localTimeNow) || (format(start) == localTimeNow)) && (localTimeNow isBefore end)
  }

  private def isAWorkingDay: Boolean = localDayNow >= 1 && localDayNow  <= 5

  def isInWorkingDaysAndHours: Boolean = isBetweenLoggingTimes && isAWorkingDay

}
