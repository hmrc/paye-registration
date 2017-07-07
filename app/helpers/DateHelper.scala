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

package helpers


import java.time.{ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

object DateHelper {
  val format: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX")

  def formatTimestamp(timeStamp: ZonedDateTime) : String = {
    val utcTimeStamp = timeStamp.withZoneSameInstant(ZoneId.of("Z"))
    format.format(utcTimeStamp)
  }

  def zonedDateTimeFromString(d:String) = ZonedDateTime.parse(d,format).withZoneSameInstant(ZoneId.of("Z"))

  def getTimestamp: ZonedDateTime = ZonedDateTime.now(ZoneId.of("Z"))

  def getTimestampString: String = formatTimestamp(getTimestamp)

  def getDateFromTimestamp(timestamp: String): ZonedDateTime = ZonedDateTime.parse(timestamp, format)
}