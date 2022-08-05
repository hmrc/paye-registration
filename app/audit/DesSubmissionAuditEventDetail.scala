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
import enums.AddressTypes
import helpers.JodaTimeJsonFormatHelper
import models.submission.DESSubmission
import play.api.libs.json.{JsObject, Json, Writes}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

case class DesSubmissionAuditEventDetail(externalId: String,
                                         authProviderId: String,
                                         regId: String,
                                         ctutr: Option[String],
                                         desSubmissionState: String,
                                         jsSubmission: JsObject,
                                         auditRefs: Map[AddressTypes.Value, String])

object DesSubmissionAuditEventDetail {

  import RegistrationAuditEvent.{AUTH_PROVIDER_ID, DES_SUBMISSION_STATE, EXTERNAL_ID}

  implicit val writes = new Writes[DesSubmissionAuditEventDetail] {
    def writes(detail: DesSubmissionAuditEventDetail) = {
      val ctutrTuple = detail.ctutr map( utr =>
        Json.obj("ctutr" -> utr)
      )

      val event = Json.obj(
        EXTERNAL_ID -> detail.externalId,
        AUTH_PROVIDER_ID -> detail.authProviderId,
        JOURNEY_ID -> detail.regId,
        DES_SUBMISSION_STATE -> detail.desSubmissionState
      ) ++ detail.jsSubmission.deepMerge(auditRefsJson(detail.auditRefs))

      if(ctutrTuple.isDefined) event ++ ctutrTuple.get else event
    }

    def auditRefsJson(refs: Map[AddressTypes.Value, String]): JsObject = {
      refs.get(AddressTypes.roAdddress).map{ ro =>
        Json.obj("payAsYouEarn" ->
          Json.obj("limitedCompany" ->
            Json.obj("registeredOfficeAddress" ->
              Json.obj("auditRef" -> Json.toJson[String](ro))
            )
          )
        )}.getOrElse(Json.obj()).deepMerge(
      refs.get(AddressTypes.ppobAdddress).map{ ppob =>
        Json.obj("payAsYouEarn" ->
          Json.obj("limitedCompany" ->
            Json.obj("businessAddress" ->
              Json.obj("auditRef" -> Json.toJson[String](ppob))
            )
          )
        )}.getOrElse(Json.obj())).deepMerge(
      refs.get(AddressTypes.correspondenceAdddress).map{ corresp =>
        Json.obj("payAsYouEarn" ->
          Json.obj("employingPeople" ->
            Json.obj("payeCorrespondenceAddress" ->
              Json.obj("auditRef" -> Json.toJson[String](corresp))
            )
          )
        )}.getOrElse(Json.obj()))
    }
  }
}

class DesSubmissionEvent(details: DesSubmissionAuditEventDetail)(implicit hc: HeaderCarrier)
  extends RegistrationAuditEvent("payeRegistrationSubmission", None, Json.toJson(details).as[JsObject])(hc)

object DesSubmissionEvent extends JodaTimeJsonFormatHelper {
  implicit val format = Json.format[ExtendedDataEvent]
}


class FailedDesSubmissionEvent(regId: String, details: DESSubmission)(implicit hc: HeaderCarrier)
  extends RegistrationAuditEvent("payeRegistrationSubmissionFailure", None, Json.obj("submission" -> details, JOURNEY_ID -> regId))(hc)

object FailedDesSubmissionEvent extends JodaTimeJsonFormatHelper {
  implicit val format = Json.format[ExtendedDataEvent]
}
