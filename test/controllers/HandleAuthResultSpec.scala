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

package controllers

import auth.{AuthResourceNotFound, Authorised, NotAuthorised, NotLoggedInOrAuthorised}
import helpers.PAYERegSpec
import play.api.mvc.Results.Ok
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}

import scala.concurrent.Future

class HandleAuthResultSpec extends PAYERegSpec {
  implicit val hc = HeaderCarrier(sessionId = Some(SessionId("session-123")))

  "logAndSendResult" should {
    "return a correct Result" when {
      "AuthorisationResult is Authorised" in {
        val response = Authorised("testId").ifAuthorised("regId", "TestController", "TestMethod") {
          Future.successful(Ok("All good"))
        }

        status(response) mustBe OK
        contentAsString(response) mustBe "All good"
      }

      "AuthorisationResult is NotLoggedInOrAuthorised" in {
        val response = NotLoggedInOrAuthorised.ifAuthorised("regId", "TestController", "TestMethod") {
          Future.successful(Ok("All good"))
        }

        status(response) mustBe FORBIDDEN
      }

      "AuthorisationResult is NotAuthorised" in {
        val response = NotAuthorised("testId").ifAuthorised("regId", "TestController", "TestMethod") {
          Future.successful(Ok("All good"))
        }

        status(response) mustBe FORBIDDEN
      }

      "AuthorisationResult is AuthResourceNotFound" in {
        val response = AuthResourceNotFound("testId").ifAuthorised("regId", "TestController", "TestMethod") {
          Future.successful(Ok("All good"))
        }

        status(response) mustBe NOT_FOUND
      }
    }
  }
}
