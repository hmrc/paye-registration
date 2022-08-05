/*
 * Copyright 2022 HM Revenue & Customs
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

package audit

import helpers.JodaTimeJsonFormatHelper
import play.api.libs.json.{JsObject, Json, Writes}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

case class DesTopUpAuditEventDetail(regId: String,
                                    jsSubmission: JsObject)

object DesTopUpAuditEventDetail {
  import RegistrationAuditEvent.JOURNEY_ID

  implicit val writes = new Writes[DesTopUpAuditEventDetail] {
    def writes(detail: DesTopUpAuditEventDetail) = {
      Json.obj(
        JOURNEY_ID -> detail.regId
      ) ++ detail.jsSubmission
    }
  }
}

class DesTopUpEvent(details: DesTopUpAuditEventDetail)(implicit hc: HeaderCarrier)
  extends RegistrationAuditEvent("payeRegistrationAdditionalData", None, Json.toJson(details).as[JsObject])(hc)

object DesTopUpEvent extends JodaTimeJsonFormatHelper {
  implicit val format = Json.format[ExtendedDataEvent]
}
