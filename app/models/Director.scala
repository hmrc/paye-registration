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

import helpers.DirectorValidator
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class Director(name: Name,
                    nino: Option[String])

case class Name(forename: Option[String],
                otherForenames: Option[String],
                surname: Option[String],
                title: Option[String])

object Name extends DirectorValidator {
  implicit val format: Format[Name] =
    (
      (__ \ "forename").formatNullable[String](nameValidator) and
      (__ \ "other_forenames").formatNullable[String](nameValidator) and
      (__ \ "surname").formatNullable[String](nameValidator) and
      (__ \ "title").formatNullable[String](titleValidator)
    )(Name.apply, unlift(Name.unapply))

  val writesDES: Writes[Name] = new Writes[Name] {
    override def writes(name: Name): JsValue = {
      val successWrites = (
        (__ \ "firstName").writeNullable[String] and
        (__ \ "middleName").writeNullable[String] and
        (__ \ "lastName").writeNullable[String] and
        (__ \ "title").writeNullable[String]
      )(unlift(Name.unapply))

      Json.toJson(name)(successWrites).as[JsObject]
    }
  }
}

object Director extends DirectorValidator {
  implicit val format: Format[Director] =
    (
      (__ \ "director").format[Name] and
      (__ \ "nino").formatNullable[String](ninoValidator)
    )(Director.apply, unlift(Director.unapply))

  val writesDES: Writes[Director] = new Writes[Director] {
    override def writes(director: Director): JsValue = {
      val successWrites = (
        (__ \ "directorName").write[Name](Name.writesDES) and
        (__ \ "directorNINO").writeNullable[String](ninoValidator)
      )(unlift(Director.unapply))

      Json.toJson(director)(successWrites).as[JsObject]
    }
  }

  val seqWritesDES: Writes[Seq[Director]] = new Writes[Seq[Director]] {
    override def writes(directors: Seq[Director]): JsValue = Json.toJson(directors map (d => Json.toJson(d)(writesDES)))
  }
}
