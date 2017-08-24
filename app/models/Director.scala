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

import models.validation.{BaseJsonFormatting, DesValidation}
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class Director(name: Name,
                    nino: Option[String])

case class Name(forename: Option[String],
                otherForenames: Option[String],
                surname: Option[String],
                title: Option[String])

object Name {
  def format(formatters: BaseJsonFormatting) = {
    formatters match {
      case DesValidation => (
        (__ \ "firstName").formatNullable[String] and
        (__ \ "middleName").formatNullable[String] and
        (__ \ "lastName").formatNullable[String] and
        (__ \ "title").formatNullable[String]
      )(Name.apply, unlift(Name.unapply))
      case _ => (
        (__ \ "forename").formatNullable[String](formatters.directorNameFormat) and
        (__ \ "other_forenames").formatNullable[String](formatters.directorNameFormat) and
        (__ \ "surname").formatNullable[String](formatters.directorNameFormat) and
        (__ \ "title").formatNullable[String](formatters.directorTitleFormat)
      )(Name.apply, unlift(Name.unapply))
    }
  }
}

object Director {
  def format(formatters: BaseJsonFormatting): Format[Director] = {
    formatters match {
      case DesValidation => (
        (__ \ "directorName").format[Name](Name.format(formatters)) and
        (__ \ "directorNINO").formatNullable[String](formatters.directorNinoFormat)
      )(Director.apply, unlift(Director.unapply))
      case _ => (
        (__ \ "director").format[Name](Name.format(formatters)) and
        (__ \ "nino").formatNullable[String](formatters.directorNinoFormat)
      )(Director.apply, unlift(Director.unapply))
    }
  }

  def directorSequenceReader(formatters: BaseJsonFormatting) = Reads.seq[Director](Director.format(formatters))

  def directorSequenceWriter(formatters: BaseJsonFormatting): Writes[Seq[Director]] = new Writes[Seq[Director]] {
    override def writes(directors: Seq[Director]): JsValue = Json.toJson(directors map (d => Json.toJson(d)(format(formatters))))
  }
}