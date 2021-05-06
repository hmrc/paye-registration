/*
 * Copyright 2021 HM Revenue & Customs
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

import models.validation.BaseJsonFormatting
import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, Reads, Writes, __}

case class DigitalContactDetails(email: Option[String],
                                 phoneNumber: Option[String],
                                 mobileNumber: Option[String])


object DigitalContactDetails {
  implicit val writes: Writes[DigitalContactDetails] = Json.writes[DigitalContactDetails]

  def reads(formatter: BaseJsonFormatting): Reads[DigitalContactDetails] = (
    (__ \ "email").readNullable[String](formatter.emailAddressReads) and
    (__ \ "phoneNumber").readNullable[String](formatter.phoneNumberReads) and
    (__ \ "mobileNumber").readNullable[String](formatter.phoneNumberReads)
  )(DigitalContactDetails.apply _)
}
