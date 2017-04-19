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

import fixtures.RegistrationFixture
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.test.UnitSpec

class DesSubmissionEventSpec extends UnitSpec with RegistrationFixture {

  "DesSubmissionEventDetail" should {

    val externalId = "Ext-123456789"
    val authProviderId = "apid001"
    val desSubmissionState = "full"

    "construct full json as per definition" in {
      val expected = Json.parse(
        s"""
          |{
          |   "externalId": "$externalId",
          |   "authProviderId": "$authProviderId",
          |   "journeyId": "${validRegistration.registrationID}",
          |   "desSubmissionState": "$desSubmissionState"
          |}
        """.stripMargin)

      val testModel = DesSubmissionAuditEventDetail(
        externalId,
        authProviderId,
        desSubmissionState,
        Json.toJson(validRegistration).as[JsObject]
      )
      Json.toJson(testModel)(DesSubmissionAuditEventDetail.writes) shouldBe expected
    }
  }
}

