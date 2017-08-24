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

import models.{DigitalContactDetails, PAYEContact}
import play.api.libs.json.Reads.pattern
import play.api.libs.json._

object Validation {

  def readToFmt(rds: Reads[String])(implicit wts: Writes[String]): Format[String] = Format(rds, wts)

  val completionCapacityRegex = """^[A-Za-z0-9 '\-]{1,100}$"""
}

trait IncorporationValidator {
  import Validation._

  val crnValidator: Format[String] =
    readToFmt(pattern("^(\\d{1,8}|([AaFfIiOoRrSsZz][Cc]|[Cc][Uu]|[Ss][AaEeFfIiRrZz]|[Ee][Ss])\\d{1,6}|([IiSs][Pp]|[Nn][AaFfIiOoPpRrVvZz]|[Rr][Oo])[\\da-zA-Z]{1,6})$".r))
}

trait PAYEBaseValidator {
  import Validation._

  def validCompletionCapacity(cap: String): Boolean = cap.matches(completionCapacityRegex)

  def validPAYEContact(contact: PAYEContact): Boolean = validDigitalContactDetails(contact.contactDetails.digitalContactDetails)

  def validDigitalContactDetails(deets: DigitalContactDetails): Boolean =
    deets.email.isDefined || deets.phoneNumber.isDefined || deets.mobileNumber.isDefined
}