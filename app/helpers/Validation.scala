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

import java.time.LocalDate

import models.{DigitalContactDetails, PAYEContact}
import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads.{maxLength, minLength, pattern}
import play.api.libs.json._


object Validation {

  def readToFmt(rds: Reads[String])(implicit wts: Writes[String]): Format[String] = Format(rds, wts)

  val addressLineRegex = """^[a-zA-Z0-9,.\(\)/&'\"\-\\]{1}[a-zA-Z0-9, .\(\)/&'\"\-\\]{0,26}$"""
  val addressLine4Regex = """^[a-zA-Z0-9,.\(\)/&'\"\-\\]{1}[a-zA-Z0-9, .\(\)/&'\"\-\\]{0,17}$"""
  val postcodeRegex = """^[A-Z]{1,2}[0-9][0-9A-Z]? [0-9][A-Z]{2}$"""
  val countryRegex = """^[A-Za-z0-9]{1}[A-Za-z 0-9]{0,19}$"""

  val phoneNumberRegex = """^[0-9 ]{1,20}$"""
  val emailRegex = """^([A-Za-z0-9\-_.]+)@([A-Za-z0-9\-_.]+)\.[A-Za-z0-9\-_.]{2,3}$"""
  val maxEmailLength = 70

  val completionCapacityRegex = """^[A-Za-z0-9 '\-]{1,100}$"""

  val companyNameRegex = """^[A-Za-z 0-9\-,.()/'&\"!%*_+:@<>?=;]{1,160}$"""
  val tradingNameRegex = """^[A-Za-z0-9\-,.()/&'!][A-Za-z 0-9\-,.()/&'!]{0,34}$"""
  val natureOfBusinessRegex = """^[A-Za-z 0-9\-,/&']{1,100}$"""

  val nameRegex = """^[A-Za-z 0-9\-';]{1,100}$"""
  val titleRegex = """^[A-Za-z ]{1,20}$"""

  val minDate = LocalDate.of(1900,1,1)
}

trait CompanyDetailsValidator {

  import Validation._

  val companyNameValidator: Format[String] = readToFmt(pattern(companyNameRegex.r))
  val tradingNameValidator: Format[String] = readToFmt(pattern(tradingNameRegex.r))
}

trait PAYEContactDetailsValidator {
  import Validation._

  val nameValidator: Format[String] =
    readToFmt(pattern("""^[A-Za-z 0-9\-']{1,100}$""".r))
}

trait DirectorValidator {
  import Validation._

  val ninoValidator: Format[String] =
    readToFmt(Reads.StringReads.filter(ValidationError("error.pattern"))(nino => {
      isValidNino(nino)
    }))

  val titleValidator: Format[String] =
    readToFmt(Reads.StringReads.filter(ValidationError("error.pattern"))(_.matches(titleRegex)))

  val nameValidator: Format[String] =
    readToFmt(Reads.StringReads.filter(ValidationError("error.pattern"))(_.matches(nameRegex)))

  private val validNinoFormat = "[[A-Z]&&[^DFIQUV]][[A-Z]&&[^DFIQUVO]]\\d{2}\\d{2}\\d{2}[A-D]{1}"
  private val invalidPrefixes = List("BG", "GB", "NK", "KN", "TN", "NT", "ZZ")
  private def hasValidPrefix(nino: String) = !invalidPrefixes.exists(nino.startsWith)

  private def isValidNino(nino: String) = nino.nonEmpty && hasValidPrefix(nino) && nino.matches(validNinoFormat)
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

trait EmploymentValidator {
  import Validation._

  val firstPaymentDateValidator: Format[LocalDate] = {
    val rds = Reads.DefaultLocalDateReads.filter(ValidationError("invalid date - too early"))(date => {
      !beforeMinDate(date)
    })

    Format(rds, Writes.DefaultLocalDateWrites)
  }

  private def beforeMinDate(date: LocalDate): Boolean = {
    date.isBefore(minDate)
  }
}
