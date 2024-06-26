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

package auth

import helpers.PAYERegSpec
import org.mockito.Mockito._
import org.scalatest._
import play.api.mvc.Results
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class AuthorisationSpec extends PAYERegSpec with BeforeAndAfter {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val authorisation = new Authorisation {
    val resourceConn = mockRegistrationRepository
    val authConnector = mockAuthConnector
  }

  override def beforeEach(): Unit = {
    reset(mockRegistrationRepository)
    reset(mockAuthConnector)
  }

  val regId = "xxx"
  val testInternalId = "foo"

  "isAuthenticated" should {
    "provided a logged in auth result when there is a valid bearer token" in {
      AuthorisationMocks.mockAuthenticated(testInternalId)

      val result = authorisation.isAuthenticated {
        _ => Future.successful(Results.Ok)
      }
      val response = await(result)
      response.header.status mustBe OK
    }

    "indicate there's no logged in user where there isn't a valid bearer token" in {
      AuthorisationMocks.mockNotAuthenticated()

      val result = authorisation.isAuthenticated {
        _ => Future.successful(Results.Ok)
      }

      an[Exception] mustBe thrownBy(await(result))
    }
  }

  "isAuthorised" should {
    "throw an Exception if an error occurred other than Authorisation" in {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      val result = authorisation.isAuthorised(regId) {
        authResult => {
          authResult mustBe Authorised(testInternalId)
          Future.failed(new Exception("Something wrong"))
        }
      }

      an[Exception] mustBe thrownBy(await(result))
    }

    "indicate there's no logged in user where there isn't a valid bearer token" in {
      AuthorisationMocks.mockNotAuthenticated()

      val result = authorisation.isAuthorised("xxx") { authResult => {
        authResult mustBe NotLoggedInOrAuthorised
        Future.successful(Results.Forbidden)
      }
      }
      val response = await(result)
      response.header.status mustBe FORBIDDEN
    }

    "provided an authorised result when logged in and a consistent resource" in {
      AuthorisationMocks.mockAuthorised(regId, testInternalId)

      val result = authorisation.isAuthorised(regId) {
        authResult => {
          authResult mustBe Authorised(testInternalId)
          Future.successful(Results.Ok)
        }
      }
      val response = await(result)
      response.header.status mustBe OK
    }

    "provided a not-authorised result when logged in and an inconsistent resource" in {
      val regId = "xxx"
      val testInternalId = "foo"

      AuthorisationMocks.mockNotAuthorised(regId, testInternalId)

      val result = authorisation.isAuthorised(regId) {
        authResult => {
          authResult mustBe NotAuthorised(testInternalId)
          Future.successful(Results.Ok)
        }
      }
      val response = await(result)
      response.header.status mustBe OK
    }

    "provide a not-found result when logged in and no resource for the identifier" in {
      val regId = "xxx"
      val testInternalId = "tiid"

      AuthorisationMocks.mockAuthResourceNotFound(regId, testInternalId)

      val result = authorisation.isAuthorised("xxx"){ authResult => {
        authResult mustBe AuthResourceNotFound(testInternalId)
        Future.successful(Results.Ok)
        }
      }
      val response = await(result)
      response.header.status mustBe OK
    }
  }
}
