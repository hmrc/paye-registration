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

import models.validation.BaseJsonFormatting
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class SICCode(code: Option[String],
                   description: Option[String])

object SICCode {
  implicit val writes: Writes[SICCode] = Json.writes[SICCode]

  def reads(formatters: BaseJsonFormatting): Reads[SICCode] = (
    (__ \ "code").readNullable[String] and
    (__ \ "description").readNullable[String](formatters.natureOfBusinessReads)
  )(SICCode.apply _)

  def sicCodeSequenceReader(formatters: BaseJsonFormatting) = Reads.seq[SICCode](SICCode.reads(formatters))

  def sicCodeSequenceWriter(formatters: BaseJsonFormatting): Writes[Seq[SICCode]] = new Writes[Seq[SICCode]] {
    override def writes(directors: Seq[SICCode]): JsValue = Json.toJson(directors map (d => Json.toJson(d)))
  }
}
