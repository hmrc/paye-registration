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

package models.incorporation

import java.time.LocalDate

import models.IncorporationValidator
import play.api.libs.json.{Reads, __}
import play.api.libs.functional.syntax._

case class IncorpStatusUpdate(transactionId: String,
                              status: String,
                              crn: Option[String],
                              incorporationDate: Option[LocalDate],
                              description: Option[String],
                              timestamp: LocalDate)

object IncorpStatusUpdate extends IncorporationValidator {
  implicit val reads: Reads[IncorpStatusUpdate] = (
    (__ \\ "IncorpSubscriptionKey" \ "transactionId").read[String] and
    (__ \\ "IncorpStatusEvent"     \ "status").read[String] and
    (__ \\ "IncorpStatusEvent"     \ "crn").readNullable[String](crnValidator) and
    (__ \\ "IncorpStatusEvent"     \ "incorporationDate").readNullable[LocalDate] and
    (__ \\ "IncorpStatusEvent"     \ "description").readNullable[String] and
    (__ \\ "IncorpStatusEvent"     \ "timestamp").read[LocalDate]
  )(IncorpStatusUpdate.apply _)
}
