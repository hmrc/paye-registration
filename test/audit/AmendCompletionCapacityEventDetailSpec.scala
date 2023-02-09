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

import helpers.PAYERegSpec
import models.submission.DESCompletionCapacity
import play.api.libs.json.Json

class AmendCompletionCapacityEventDetailSpec extends PAYERegSpec {
  "AmendCompletionCapacityEventDetail" should {
    val regId = "testRegId"
    val externalUserId = "testExternalId"
    val authProviderId = "testAuthProviderId"

    "have the correct result when the new completion capacity is director" in {
      val expectedDetail = Json.parse(
        s"""{
           |    "externalUserId": "$externalUserId",
           |    "authProviderId": "$authProviderId",
           |    "journeyId": "$regId",
           |    "previousCompletionCapacity": "Other",
           |    "previousCompletionCapacityOther": "oldcc",
           |    "newCompletionCapacity": "Director"
           | }
        """.stripMargin)

      val testModel = AmendCompletionCapacityEventDetail(
        externalUserId,
        authProviderId,
        regId,
        DESCompletionCapacity.buildDESCompletionCapacity(Some("oldCC")),
        DESCompletionCapacity.buildDESCompletionCapacity(Some("director")))
      Json.toJson(testModel) mustBe expectedDetail
    }

    "have the correct result when the new completion capacity is secretary" in {
      val expectedDetail = Json.parse(
        s"""{
           |    "externalUserId": "$externalUserId",
           |    "authProviderId": "$authProviderId",
           |    "journeyId": "$regId",
           |    "previousCompletionCapacity": "Director",
           |    "newCompletionCapacity": "Company secretary"
           | }
        """.stripMargin)

      val testModel = AmendCompletionCapacityEventDetail(
        externalUserId,
        authProviderId,
        regId,
        DESCompletionCapacity.buildDESCompletionCapacity(Some("director")),
        DESCompletionCapacity.buildDESCompletionCapacity(Some("company secretary")))
      Json.toJson(testModel) mustBe expectedDetail
    }

    "have the correct result when the new completion capacity is agent" in {
      val expectedDetail = Json.parse(
        s"""{
           |    "externalUserId": "$externalUserId",
           |    "authProviderId": "$authProviderId",
           |    "journeyId": "$regId",
           |    "previousCompletionCapacity": "Director",
           |    "newCompletionCapacity": "Agent"
           | }
        """.stripMargin)

      val testModel = AmendCompletionCapacityEventDetail(
        externalUserId,
        authProviderId,
        regId,
        DESCompletionCapacity.buildDESCompletionCapacity(Some("director")),
        DESCompletionCapacity.buildDESCompletionCapacity(Some("agent")))
      Json.toJson(testModel) mustBe expectedDetail
    }

    "have the correct result when the new completion capacity is other" in {
      val expectedDetail = Json.parse(
        s"""{
           |    "externalUserId": "$externalUserId",
           |    "authProviderId": "$authProviderId",
           |    "journeyId": "$regId",
           |    "previousCompletionCapacity": "Director",
           |    "newCompletionCapacity": "Other",
           |    "newCompletionCapacityOther": "newcc"
           | }
        """.stripMargin)

      val testModel = AmendCompletionCapacityEventDetail(
        externalUserId,
        authProviderId,
        regId,
        DESCompletionCapacity.buildDESCompletionCapacity(Some("director")),
        DESCompletionCapacity.buildDESCompletionCapacity(Some("newCC")))
      Json.toJson(testModel) mustBe expectedDetail
    }

    "have the correct result when the new completion capacity is other and previous value was other" in {
      val expectedDetail = Json.parse(
        s"""{
           |    "externalUserId": "$externalUserId",
           |    "authProviderId": "$authProviderId",
           |    "journeyId": "$regId",
           |    "previousCompletionCapacity": "Other",
           |    "previousCompletionCapacityOther": "oldcc",
           |    "newCompletionCapacity": "Other",
           |    "newCompletionCapacityOther": "newcc"
           | }
        """.stripMargin)

      val testModel = AmendCompletionCapacityEventDetail(
        externalUserId,
        authProviderId,
        regId,
        DESCompletionCapacity.buildDESCompletionCapacity(Some("oldCC")),
        DESCompletionCapacity.buildDESCompletionCapacity(Some("newCC")))
      Json.toJson(testModel) mustBe expectedDetail
    }
  }
}
