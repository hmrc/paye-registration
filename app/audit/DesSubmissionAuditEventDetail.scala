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

package audit

import play.api.libs.json.{JsObject, Json, Writes}
import uk.gov.hmrc.play.http.HeaderCarrier

case class DesSubmissionAuditEventDetail(externalId: String,
                                         authProviderId: String,
                                         regId: String,
                                         ctutr: Option[String],
                                         desSubmissionState: String,
                                         jsSubmission: JsObject)

object DesSubmissionAuditEventDetail {

  import RegistrationAuditEvent.{EXTERNAL_ID, AUTH_PROVIDER_ID, JOURNEY_ID, DES_SUBMISSION_STATE}

  implicit val writes = new Writes[DesSubmissionAuditEventDetail] {
    def writes(detail: DesSubmissionAuditEventDetail) = {
      val regMetadata = Json.obj("businessType" -> "Limited company")
      val ctutrTuple = detail.ctutr map( utr =>
        Json.obj("ctutr" -> utr)
      )

      val event = Json.obj(
        EXTERNAL_ID -> detail.externalId,
        AUTH_PROVIDER_ID -> detail.authProviderId,
        JOURNEY_ID -> detail.regId,
        DES_SUBMISSION_STATE -> detail.desSubmissionState
      ) ++ detail.jsSubmission

      if(ctutrTuple.isDefined) event ++ ctutrTuple.get else event
    }
  }
}

class DesSubmissionEvent(details: DesSubmissionAuditEventDetail)(implicit hc: HeaderCarrier)
  extends RegistrationAuditEvent("payeRegistrationSubmission", None, Json.toJson(details).as[JsObject])(hc)
