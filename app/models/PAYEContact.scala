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

package models

import models.validation.{APIValidation, BaseJsonFormatting}
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, __}

case class PAYEContact(contactDetails: PAYEContactDetails,
                       correspondenceAddress: Address)

case class PAYEContactDetails(name: String,
                              digitalContactDetails: DigitalContactDetails)

object PAYEContactDetails {
  def formatter(formatter: BaseJsonFormatting): Format[PAYEContactDetails] = (
    (__ \ "name").format[String](formatter.nameReads) and
    (__ \ "digitalContactDetails").format[DigitalContactDetails](DigitalContactDetails.reads(formatter))
  )(PAYEContactDetails.apply, unlift(PAYEContactDetails.unapply))
}

object PAYEContact {
  def format(formatter: BaseJsonFormatting): Format[PAYEContact] = (
    (__ \ "contactDetails").format[PAYEContactDetails](PAYEContactDetails.formatter(formatter)) and
    (__ \ "correspondenceAddress").format[Address](Address.reads(formatter))
  )(PAYEContact.apply, unlift(PAYEContact.unapply))

  implicit val format: Format[PAYEContact] = format(APIValidation)
}
