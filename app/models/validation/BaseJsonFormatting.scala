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

package models.validation

import java.text.Normalizer
import java.text.Normalizer.Form
import java.time.LocalDate

import models.Eligibility
import play.api.data.validation.ValidationError
import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.collection.Seq

trait BaseJsonFormatting {
  private val companyNameRegex = """^[A-Za-z 0-9\-,.()/'&\"!%*_+:@<>?=;]{1,160}$"""
  private val forbiddenPunctuation = Set('[', ']', '{', '}', '#', '«', '»')
  private val illegalCharacters = Map('æ' -> "ae", 'Æ' -> "AE", 'œ' -> "oe", 'Œ' -> "OE", 'ß' -> "ss", 'ø' -> "o", 'Ø' -> "O")

  def readToFmt(rds: Reads[String])(implicit wts: Writes[String]): Format[String] = Format(rds, wts)

  def standardRead = Reads.StringReads

  def cleanseCompanyName(companyName: String): String = Normalizer.normalize(
    companyName.map(c => if(illegalCharacters.contains(c)) illegalCharacters(c) else c).mkString,
    Form.NFD
  ).replaceAll("[^\\p{ASCII}]", "").filterNot(forbiddenPunctuation)

  val companyNameFormatter = new Format[String] {
    override def reads(json: JsValue) = json match {
      case JsString(companyName) => if(cleanseCompanyName(companyName).matches(companyNameRegex)) {
        JsSuccess(companyName)
      } else {
        JsError(Seq(JsPath() -> Seq(ValidationError("Invalid company name"))))
      }
      case _ => JsError(Seq(JsPath() -> Seq(ValidationError("error.expected.jsstring"))))
    }

    override def writes(o: String) = Writes.StringWrites.writes(o)
  }

  val eligibilityFormat: Format[Eligibility] = Json.format[Eligibility]
  val crnReads: Reads[String] = Reads.pattern("^(\\d{1,8}|([AaFfIiOoRrSsZz][Cc]|[Cc][Uu]|[Ss][AaEeFfIiRrZz]|[Ee][Ss])\\d{1,6}|([IiSs][Pp]|[Nn][AaFfIiOoPpRrVvZz]|[Rr][Oo])[\\da-zA-Z]{1,6})$".r)
  val completionCapacityReads: Reads[String]
  val phoneNumberReads: Reads[String]
  val emailAddressReads: Reads[String]
  val nameReads: Reads[String]
  val natureOfBusinessReads: Reads[String]
  val tradingNameFormat: Format[String]

  //Address validation
  val addressLineValidate: Reads[String]
  val addressLine4Validate: Reads[String]
  val postcodeValidate: Reads[String]
  val countryValidate: Reads[String]

  val firstPaymentDateFormat: Format[LocalDate]
  val directorNameFormat: Format[String]
  val directorTitleFormat: Format[String]
  val directorNinoFormat: Format[String]

}
