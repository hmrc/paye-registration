/*
 * Copyright 2019 HM Revenue & Customs
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

import models.submission.DESCompletionCapacity
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

case class AmendCompletionCapacityEventDetail(externalUserId: String,
                                              authProviderId: String,
                                              regId: String,
                                              previousCC: DESCompletionCapacity,
                                              newCC: DESCompletionCapacity
                                             )

object AmendCompletionCapacityEventDetail {
  import RegistrationAuditEvent.{AUTH_PROVIDER_ID, EXTERNAL_USER_ID, JOURNEY_ID}

  implicit val writes = new Writes[AmendCompletionCapacityEventDetail] {
    val writesPreviousCC: Writes[DESCompletionCapacity] = new Writes[DESCompletionCapacity] {
      override def writes(cc: DESCompletionCapacity): JsValue = {
        val successWrites = (
          (__ \ "previousCompletionCapacity").write[String](DESCompletionCapacity.capitalizeWrites) and
          (__ \ "previousCompletionCapacityOther").writeNullable[String]
        )(unlift(DESCompletionCapacity.unapply))

        Json.toJson(cc)(successWrites).as[JsObject]
      }
    }

    val writesNewCC: Writes[DESCompletionCapacity] = new Writes[DESCompletionCapacity] {
      override def writes(cc: DESCompletionCapacity): JsValue = {
        val successWrites = (
          (__ \ "newCompletionCapacity").write[String](DESCompletionCapacity.capitalizeWrites) and
          (__ \ "newCompletionCapacityOther").writeNullable[String]
        )(unlift(DESCompletionCapacity.unapply))

        Json.toJson(cc)(successWrites).as[JsObject]
      }
    }

    override def writes(o: AmendCompletionCapacityEventDetail): JsValue = {
      Json.obj(
        EXTERNAL_USER_ID -> o.externalUserId,
        AUTH_PROVIDER_ID -> o.authProviderId,
        JOURNEY_ID -> o.regId
      )
      .++(Json.toJson(o.previousCC)(writesPreviousCC).as[JsObject])
      .++(Json.toJson(o.newCC)(writesNewCC).as[JsObject])
    }
  }
}

class AmendCompletionCapacityEvent(regId: String, detail: AmendCompletionCapacityEventDetail)(implicit hc: HeaderCarrier)
  extends RegistrationAuditEvent("completionCapacityAmendment", None, Json.toJson(detail).as[JsObject])(hc)

object AmendCompletionCapacityEvent {
  implicit val format = Json.format[ExtendedDataEvent]
}
