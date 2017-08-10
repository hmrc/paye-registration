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

import helpers.Validation
import models.validation.BaseValidation
import play.api.data.validation.ValidationError
import play.api.libs.json.{Json, Reads, __}
import play.api.libs.functional.syntax._

case class DigitalContactDetails(email: Option[String],
                                 phoneNumber: Option[String],
                                 mobileNumber: Option[String])


object DigitalContactDetails {

  private val emailValidate = Reads.StringReads
    .filter(ValidationError("invalid email pattern"))(_.matches(Validation.emailRegex))

  implicit val writes = Json.writes[DigitalContactDetails]

  def reads(validators: BaseValidation): Reads[DigitalContactDetails] = (
    (__ \ "email").readNullable[String](emailValidate) and
    (__ \ "phoneNumber").readNullable[String](validators.phoneNumberValidation) and
    (__ \ "mobileNumber").readNullable[String](validators.phoneNumberValidation)
  )(DigitalContactDetails.apply _)
}
