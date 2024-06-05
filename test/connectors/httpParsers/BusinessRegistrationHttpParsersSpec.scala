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

package connectors.httpParsers

import ch.qos.logback.classic.Level
import helpers.PAYERegSpec
import models.external.BusinessProfile
import play.api.http.Status.{FORBIDDEN, INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import play.api.libs.json.{JsResultException, Json}
import uk.gov.hmrc.http.{ForbiddenException, HttpResponse, NotFoundException}
import utils.LogCapturingHelper

class BusinessRegistrationHttpParsersSpec extends PAYERegSpec with LogCapturingHelper {

  val regId = "reg12345"
  val txId = "tx12345"

  lazy val validBusinessRegistrationResponse: BusinessProfile = BusinessProfile(
    registrationID = regId,
    completionCapacity = Some("director"),
    language = "ENG"
  )

  lazy val validBusinessRegistrationJson = Json.toJson(validBusinessRegistrationResponse)

  "BusinessRegistrationHttpParsers" when {

    "calling .businessProfileHttpReads(regId: String, txId: Option[String])" when {

      val rds = BusinessRegistrationHttpParsers.businessProfileHttpReads(regId)

      "response is OK and JSON is valid" must {

        "return Some(utr)" in {

          val response = HttpResponse(OK, json = validBusinessRegistrationJson, Map())
          rds.read("", "", response) mustBe validBusinessRegistrationResponse
        }
      }

      "response is OK and JSON is malformed" must {

        "throw JsResultException and log an error" in {

          val response = HttpResponse(OK, json = Json.obj(), Map())

          withCaptureOfLoggingFrom(BusinessRegistrationHttpParsers.logger) { logs =>
            intercept[JsResultException](rds.read("", "", response))
            logs.containsMsg(Level.ERROR, s"[BusinessRegistrationHttpParsers][businessProfileHttpReads] JSON returned could not be parsed to models.external.BusinessProfile model for regId: '$regId'")
          }
        }
      }

      "response is NOT_FOUND" must {

        "throw a NotFoundException and Log and Error" in {

          val response = HttpResponse(NOT_FOUND, "")

          withCaptureOfLoggingFrom(BusinessRegistrationHttpParsers.logger) { logs =>
            intercept[NotFoundException](rds.read("", "", response))
            logs.containsMsg(Level.ERROR, s"[BusinessRegistrationHttpParsers][businessProfileHttpReads] Received a NotFound status code when expecting reg document from Business-Registration for regId: '$regId'")
          }
        }
      }

      "response is FORBIDDEN" must {

        "throw a ForbiddenException and Log and Error" in {

          val response = HttpResponse(FORBIDDEN, "")

          withCaptureOfLoggingFrom(BusinessRegistrationHttpParsers.logger) { logs =>
            intercept[ForbiddenException](rds.read("", "", response))
            logs.containsMsg(Level.ERROR, s"[BusinessRegistrationHttpParsers][businessProfileHttpReads] Received a Forbidden status code when expecting reg document from Business-Registration for regId: '$regId'")
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Upstream Error response and log an error" in {

          withCaptureOfLoggingFrom(BusinessRegistrationHttpParsers.logger) { logs =>
            intercept[Exception](rds.read("", "/foo/bar", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[BusinessRegistrationHttpParsers][ctUtrHttpReads] Calling url: '/foo/bar' returned unexpected status: '$INTERNAL_SERVER_ERROR' for regId: '$regId'")
          }
        }
      }
    }
  }
}
