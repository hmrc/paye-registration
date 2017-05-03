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

import helpers.Validation
import play.api.data.validation.ValidationError
import play.api.libs.json.{Reads, Json, __}
import play.api.libs.functional.syntax._

case class SICCode(
                  code: Option[String],
                  description: Option[String]
                  )

object SICCode {
  private val natureOfBusinessValidate = Reads.StringReads.filter(ValidationError("Invalid nature of business"))(_.matches(Validation.natureOfBusinessRegex))

  implicit val writes = Json.writes[SICCode]

  implicit val reads: Reads[SICCode] = (
    (__ \ "code").readNullable[String] and
    (__ \ "description").readNullable[String](natureOfBusinessValidate)
  )(SICCode.apply _)
}
