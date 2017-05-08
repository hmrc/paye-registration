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

package models

import auth.Crypto
import play.api.libs.json.{Format, Json, __}
import play.api.libs.functional.syntax._

case class EmpRefNotification(empRef: Option[String],
                              timestamp: String,
                              status: String)

object EmpRefNotification {

  implicit val apiFormat = Json.format[EmpRefNotification]

  private val empRefMongoFormat = Format[String](Crypto.rds, Crypto.wts)

  val mongoFormat = (
    (__ \ "empRef").formatNullable[String](empRefMongoFormat) and
    (__ \ "timestamp").format[String] and
    (__ \ "status").format[String]
    )(EmpRefNotification.apply, unlift(EmpRefNotification.unapply _))
}
