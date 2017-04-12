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

sealed trait DESSubmission
object DESSubmission {
  implicit val writes: Writes[DESSubmission] =
    new Writes[DESSubmission] {
      override def writes(o: DESSubmission): JsValue = o match {
        case s: DESSubmissionModel => DESSubmissionModel.writes.writes(s)
        case s: TopUpDESSubmission => TopUpDESSubmission.writes.writes(s)
      }
    }
}

case class DESSubmissionModel(acknowledgementReference: String,
                              crn: Option[String],
                              company: DESCompanyDetails,
                              directors: Seq[DESDirector],
                              payeContact: DESPAYEContact,
                              businessContact: DESBusinessContact,
                              sicCodes: Seq[DESSICCode],
                              employment: DESEmployment,
                              completionCapacity: DESCompletionCapacity) extends DESSubmission

object DESSubmissionModel {
  implicit val writes: Writes[DESSubmissionModel] =
    (
      (__ \ "acknowledgement-reference").write[String] and
      (__ \ "crn").writeNullable[String] and
      (__ \ "company").write[DESCompanyDetails] and
      (__ \ "directors").write[Seq[DESDirector]] and
      (__ \ "paye-contact").write[DESPAYEContact] and
      (__ \ "business-contact").write[DESBusinessContact] and
      (__ \ "sic-codes").write[Seq[DESSICCode]] and
      (__ \ "employment").write[DESEmployment] and
      (__ \ "completion-capacity").write[DESCompletionCapacity]
      )(unlift(DESSubmissionModel.unapply))
}

case class TopUpDESSubmission(acknowledgementReference: String,
                              status: String,
                              crn: Option[String]) extends DESSubmission

object TopUpDESSubmission {
  implicit val writes: Writes[TopUpDESSubmission] =
    (
      (__ \ "acknowledgement-reference").write[String] and
      (__ \ "status").write[String] and
      (__ \ "crn").writeNullable[String]
    )(unlift(TopUpDESSubmission.unapply))
}