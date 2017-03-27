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

import play.api.data.validation.ValidationError
import play.api.libs.json._
import Reads.{maxLength, minLength, pattern}
import play.api.libs.functional.syntax._


object Validation {

  def length(maxLen: Int, minLen: Int = 1): Reads[String] = maxLength[String](maxLen) keepAnd minLength[String](minLen)

  def readToFmt(rds: Reads[String])(implicit wts: Writes[String]): Format[String] = Format(rds, wts)

  def lengthFmt(maxLen: Int, minLen: Int = 1): Format[String] = readToFmt(length(maxLen, minLen))

  def yyyymmddValidator: Reads[String] = pattern("^[0-9]{4}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])$".r)

  def yyyymmddValidatorFmt: Format[String] = readToFmt(yyyymmddValidator)

  def withFilter[A](fmt: Format[A], error: ValidationError)(f: (A) => Boolean): Format[A] = {
    Format(fmt.filter(error)(f), fmt)
  }
}

trait CompanyDetailsValidator {

  import Validation._

  val companyNameValidator: Format[String] = readToFmt(pattern("^[A-Za-z 0-9\\-,.()/'&amp;&quot;!%*_+:@&lt;&gt;?=;]{1,160}$".r))
}

trait PAYEContactDetailsValidator {
  import Validation._

  val nameValidator: Format[String] =
    readToFmt(pattern("^[A-Za-z 0-9\\-']{1,100}$".r))
}

trait DirectorValidator {
  import Validation._

  val ninoValidator: Format[String] =
    readToFmt(Reads.StringReads.filter(ValidationError("error.pattern"))(nino => {
      isValidNino(nino)
    }))

  private val validNinoFormat = "[[A-Z]&&[^DFIQUV]][[A-Z]&&[^DFIQUVO]]\\d{2}\\d{2}\\d{2}[A-D]{1}"
  private val invalidPrefixes = List("BG", "GB", "NK", "KN", "TN", "NT", "ZZ")
  private def hasValidPrefix(nino: String) = invalidPrefixes.find(nino.startsWith).isEmpty

  private def isValidNino(nino: String) = nino.nonEmpty && hasValidPrefix(nino) && nino.matches(validNinoFormat)
}

trait IncorporationValidator {
  import Validation._

  val crnValidator: Format[String] =
    readToFmt(pattern("^(\\d{1,8}|([AaFfIiOoRrSsZz][Cc]|[Cc][Uu]|[Ss][AaEeFfIiRrZz]|[Ee][Ss])\\d{1,6}|([IiSs][Pp]|[Nn][AaFfIiOoPpRrVvZz]|[Rr][Oo])[\\da-zA-Z]{1,6})$".r))
}
