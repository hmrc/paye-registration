/*
 * Copyright 2024 HM Revenue & Customs
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

import enums.IncorporationStatus
import helpers.PAYERegSpec
import models.submission.TopUpEtmpSubmission
import play.api.libs.json.{JsObject, Json}

class EtmpTopUpAuditEventDetailSpec extends PAYERegSpec {
  "DesTopUpAuditEventDetail" should {
    val regId = "123456789"
    val ackRef = "ackRef"

    "construct full json as per definition" when {
      "incorporation is accepted" in {
        val validTopUpDESSubmission = TopUpEtmpSubmission(ackRef, IncorporationStatus.accepted, Some("AA123456"))

        val expected = Json.parse(
          s"""
             |{
             |   "journeyId": "$regId",
             |   "acknowledgementReference": "$ackRef",
             |   "status": "accepted",
             |   "payAsYouEarn": {
             |     "crn": "AA123456"
             |   }
             |}
          """.stripMargin)

        val testModel = EtmpTopUpAuditEventDetail(
          regId,
          Json.toJson[TopUpEtmpSubmission](validTopUpDESSubmission)(TopUpEtmpSubmission.auditWrites).as[JsObject]
        )
        Json.toJson(testModel)(EtmpTopUpAuditEventDetail.writes) mustBe expected
      }

      "incorporation is rejected" in {
        val validTopUpDESSubmission = TopUpEtmpSubmission(ackRef, IncorporationStatus.rejected, None)

        val expected = Json.parse(
          s"""
             |{
             |   "journeyId": "$regId",
             |   "acknowledgementReference": "$ackRef",
             |   "status": "rejected"
             |}
          """.stripMargin)

        val testModel = EtmpTopUpAuditEventDetail(
          regId,
          Json.toJson[TopUpEtmpSubmission](validTopUpDESSubmission)(TopUpEtmpSubmission.auditWrites).as[JsObject]
        )
        Json.toJson(testModel)(EtmpTopUpAuditEventDetail.writes) mustBe expected
      }
    }
  }
}
