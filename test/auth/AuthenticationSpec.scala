/*
 * Copyright 2016 HM Revenue & Customs
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

import connectors.{AuthConnector, UserIds, Authority}
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{WordSpecLike, BeforeAndAfter, ShouldMatchers}
import play.Mode
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results
import play.api.test.FakeApplication
import play.api.test.Helpers._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class AuthenticationSpec extends WordSpecLike with ShouldMatchers with MockitoSugar with BeforeAndAfter {

    implicit val hc = HeaderCarrier()

    val mockAuth = mock[AuthConnector]

    object Authenticated extends Authenticated {
        val auth = mockAuth
    }

    before {
        reset(mockAuth)
    }

    "The authentication helper" should {

        "provided a logged in auth result when there is a valid bearer token" in {

            val userIDs = UserIds("foo", "bar")
            val a = Authority("x", "", "z", userIDs)

            when(mockAuth.getCurrentAuthority()(Matchers.any())).
                thenReturn(Future.successful(Some(a)))

            val result = Authenticated.authenticated { authResult => {
                authResult shouldBe LoggedIn(a)
                Future.successful(Results.Ok)
            }
            }
            val response = await(result)
            response.header.status shouldBe OK
        }

        "indicate there's no logged in user where there isn't a valid bearer token" in {

            when(mockAuth.getCurrentAuthority()(Matchers.any())).
                thenReturn(Future.successful(None))

            val result = Authenticated.authenticated { authResult => {
                authResult shouldBe NotLoggedIn
                Future.successful(Results.Forbidden)
            }
            }
            val response = await(result)
            response.header.status shouldBe FORBIDDEN
        }
    }
}