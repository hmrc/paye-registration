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

import helpers.PAYEContactDetailsValidator
import models.validation.{APIReads, MongoReads}
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, Json, Reads, __}

case class PAYEContact(contactDetails: PAYEContactDetails,
                       correspondenceAddress: Address)

case class PAYEContactDetails(name: String,
                              digitalContactDetails: DigitalContactDetails)

object PAYEContactDetails extends PAYEContactDetailsValidator {
  def payeContactDetailsFormat(phoneValidator: Reads[String]): Format[PAYEContactDetails] = (
    (__ \ "name").format[String](nameValidator) and
    (__ \ "digitalContactDetails").format[DigitalContactDetails](DigitalContactDetails.digitalContactDetailsReads(phoneValidator))
  )(PAYEContactDetails.apply, unlift(PAYEContactDetails.unapply))
}

object PAYEContact {
  implicit val payeContactDetails = PAYEContactDetails.payeContactDetailsFormat(APIReads.phoneNumberValidation)
  implicit val addressReads = Address.reads
  implicit val addressWrites = Address.writes
  implicit val format = Json.format[PAYEContact]

  val mongoFormat: Format[PAYEContact] = {
    implicit val payeContactDetails = PAYEContactDetails.payeContactDetailsFormat(MongoReads.phoneNumberValidation)
    Json.format[PAYEContact]
  }
}
