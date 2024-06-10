/*
 * Copyright 2023 HM Revenue & Customs
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

import audit.RegistrationAuditEventConstants.JOURNEY_ID
import enums.AddressTypes
import play.api.libs.json.{JsObject, Json, Writes}

case class DesSubmissionAuditEventDetail(externalId: String,
                                         authProviderId: String,
                                         regId: String,
                                         ctutr: Option[String],
                                         desSubmissionState: String,
                                         jsSubmission: JsObject,
                                         auditRefs: Map[AddressTypes.Value, String])

object DesSubmissionAuditEventDetail {

  import RegistrationAuditEventConstants.{AUTH_PROVIDER_ID, DES_SUBMISSION_STATE, EXTERNAL_ID}

  implicit val writes: Writes[DesSubmissionAuditEventDetail] = new Writes[DesSubmissionAuditEventDetail] {
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
