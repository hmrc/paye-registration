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

package models.submission

import play.api.libs.functional.syntax._
import play.api.libs.functional.syntax.unlift
import play.api.libs.json.{JsValue, Json, Writes, __}


case class DESSubmission(acknowledgementReference: String,
                              metaData: DESMetaData,
                              limitedCompany: DESLimitedCompany,
                              employingPeople: DESEmployingPeople)

object DESSubmission {
  implicit val writes: Writes[DESSubmission] =
    (
      (__ \ "acknowledgementReference").write[String] and
      (__ \ "metaData").write[DESMetaData] and
      (__ \ "payAsYouEarn" \ "limitedCompany").write[DESLimitedCompany] and
      (__ \ "payAsYouEarn" \ "employingPeople").write[DESEmployingPeople]
      )(unlift(DESSubmission.unapply))
}

case class TopUpDESSubmission(acknowledgementReference: String,
                              status: String,
                              crn: Option[String])

object TopUpDESSubmission {
  implicit val writes: Writes[TopUpDESSubmission] =
    (
      (__ \ "acknowledgementReference").write[String] and
      (__ \ "status").write[String] and
      (__ \ "payAsYouEarn" \ "crn").writeNullable[String]
    )(unlift(TopUpDESSubmission.unapply))
}