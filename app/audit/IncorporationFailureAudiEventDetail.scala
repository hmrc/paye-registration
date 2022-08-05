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

import audit.RegistrationAuditEvent.JOURNEY_ID
import helpers.JodaTimeJsonFormatHelper
import play.api.libs.json.{JsObject, JsValue, Json, Writes}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

case class IncorporationFailureAuditEventDetail(regId: String,
                                               acknowledgementReference: String)

object IncorporationFailureAuditEventDetail {
  private val ACK_REF       = "acknowledgementReference"
  private val INCORP_STATUS = "incorporationStatus"
  private val REJECTED      = "rejected"

  implicit val incorpFailedEventWrites = new Writes[IncorporationFailureAuditEventDetail] {
    override def writes(detail: IncorporationFailureAuditEventDetail): JsValue = Json.obj(
      JOURNEY_ID      -> detail.regId,
      ACK_REF         -> detail.acknowledgementReference,
      INCORP_STATUS   -> REJECTED
    )
  }
}

class IncorporationFailureEvent(details: IncorporationFailureAuditEventDetail)(implicit hc: HeaderCarrier)
  extends RegistrationAuditEvent("incorporationFailure", None, Json.toJson(details).as[JsObject])(hc)

object IncorporationFailureEvent extends JodaTimeJsonFormatHelper {
  implicit val format = Json.format[ExtendedDataEvent]
}
