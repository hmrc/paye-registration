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

import models.validation.{APIValidation, BaseJsonFormatting, DesValidation}
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class Director(name: Name,
                    nino: Option[String])

case class Name(forename: Option[String],
                otherForenames: Option[String],
                surname: Option[String],
                title: Option[String])

object Name {
  def format(formatter: BaseJsonFormatting) = {
    formatter match {
      case DesValidation => (
        (__ \ "firstName").formatNullable[String] and
        (__ \ "middleName").formatNullable[String] and
        (__ \ "lastName").formatNullable[String] and
        (__ \ "title").formatNullable[String]
      )(Name.apply, unlift(Name.unapply))
      case _ => (
        (__ \ "forename").formatNullable[String](formatter.directorNameFormat) and
        (__ \ "other_forenames").formatNullable[String](formatter.directorNameFormat) and
        (__ \ "surname").formatNullable[String](formatter.directorNameFormat) and
        (__ \ "title").formatNullable[String](formatter.directorTitleFormat)
      )(Name.apply, unlift(Name.unapply))
    }
  }
}

object Director {
  def format(formatter: BaseJsonFormatting): Format[Director] = {
    formatter match {
      case DesValidation => (
        (__ \ "directorName").format[Name](Name.format(formatter)) and
        (__ \ "directorNINO").formatNullable[String](formatter.directorNinoFormat)
      )(Director.apply, unlift(Director.unapply))
      case _ => (
        (__ \ "director").format[Name](Name.format(formatter)) and
        (__ \ "nino").formatNullable[String](formatter.directorNinoFormat)
      )(Director.apply, unlift(Director.unapply))
    }
  }

  def seqFormat(formatter: BaseJsonFormatting): Format[Seq[Director]] = {
    val reads = Reads.seq[Director](Director.format(formatter))
    val writes = directorSequenceWriter(formatter)

    Format(reads, writes)
  }

  def directorSequenceWriter(formatter: BaseJsonFormatting): Writes[Seq[Director]] = new Writes[Seq[Director]] {
    override def writes(directors: Seq[Director]): JsValue = Json.toJson(directors map (d => Json.toJson(d)(format(formatter))))
  }

  implicit val format: Format[Director] = format(APIValidation)
  implicit val seqFormat: Format[Seq[Director]] = seqFormat(APIValidation)
}