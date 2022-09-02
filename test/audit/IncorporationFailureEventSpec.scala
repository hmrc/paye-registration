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

import helpers.PAYERegSpec
import play.api.libs.json.Json

class IncorporationFailureEventSpec extends PAYERegSpec {
  "IncorporationFailureAuditEventDetail" should {
    "construct a full set of Json" in {
      val testModel = IncorporationFailureAuditEventDetail("testRegId", "testAckRef")

      val expectedJson = Json.parse(
        """
          |{
          | "journeyId" : "testRegId",
          | "acknowledgementReference" : "testAckRef",
          | "incorporationStatus" : "rejected"
          |}
        """.stripMargin
      )

      val result = Json.toJson[IncorporationFailureAuditEventDetail](testModel)(IncorporationFailureAuditEventDetail.incorpFailedEventWrites)
      result mustBe expectedJson
    }
  }
}
