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

package utils

import java.time.{Instant, ZoneId, ZoneOffset, ZonedDateTime}
import javax.inject.{Inject, Singleton}

import helpers.DateHelper
import play.api.libs.json._

trait DateFormatter extends DateHelper {

  val dateTimeReadMongo: Reads[ZonedDateTime]  = (__ \ "$date").read[Long] map { dateTime =>
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(dateTime), ZoneOffset.UTC)
  }

  val dateTimeWriteMongo: Writes[ZonedDateTime] = new Writes[ZonedDateTime]{
    def writes(z:ZonedDateTime) = Json.obj("$date" -> z.toInstant.toEpochMilli)
  }

  val dateTimeReadApi: Reads[ZonedDateTime] = new Reads[ZonedDateTime] {
    def reads(js: JsValue) =
      try {
        JsSuccess(zonedDateTimeFromString(js.as[String]))
      }
      catch {
        case e: Throwable =>  JsError(error = e.getMessage)
      }
  }

  val dateTimeWriteApi: Writes[ZonedDateTime] = new Writes[ZonedDateTime] {
    def writes(z:ZonedDateTime) = JsString(formatTimestamp(z))
  }

  val apiFormat:Format[ZonedDateTime] = Format(dateTimeReadApi, dateTimeWriteApi)
  val mongoFormat:Format[ZonedDateTime] = Format(dateTimeReadMongo, dateTimeWriteMongo)

}


