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

import play.api.data.validation.ValidationError
import play.api.libs.json._

import scala.collection.Seq

trait BaseValidation {
  private val companyNameRegex = """^[A-Za-z 0-9\-,.()/'&\"!%*_+:@<>?=;]{1,160}$"""
  private val forbiddenPunctuation = Set('[', ']', '{', '}', '#', '«', '»')
  private val illegalCharacters = Map('æ' -> "ae", 'Æ' -> "AE", 'œ' -> "oe", 'Œ' -> "OE", 'ß' -> "ss", 'ø' -> "o", 'Ø' -> "O")

  def cleanseCompanyName(companyName: String): String = Normalizer.normalize(
    companyName.map(c => if(illegalCharacters.contains(c)) illegalCharacters(c) else c).mkString,
    Form.NFD
  ).replaceAll("[^\\p{ASCII}]", "").filterNot(forbiddenPunctuation)


  val phoneNumberValidation: Reads[String]

  val companyNameValidation = new Format[String] {
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
}
