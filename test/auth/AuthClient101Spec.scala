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
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthorisedFunctions
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthClient101Spec extends PAYERegSpec {

  object TestController extends BackendController(stubControllerComponents()) with AuthorisedFunctions {
    override val authConnector = mockAuthConnector


    def isAuthorised = Action.async { implicit request =>
      authorised() {
        Future.successful(Ok("fjndgjfn"))
      } recover {
        case _ => Forbidden
      }
    }

    def isAuthorisedWithData = Action.async { implicit request =>
      authorised().retrieve(internalId) {
        id => Future.successful(id.fold(NoContent)(s => Ok(s)))
      } recover {
        case _ => Forbidden
      }
    }

    def isAuthorisedWithCredId = Action.async { implicit request =>
      authorised().retrieve(credentials) {
        case Some(cred) =>
          Future.successful(Ok(cred.providerId))
        case _ =>
          Future.failed(new Exception("missing cred ID"))
      } recover {
        case _ => Forbidden
      }
    }

    def isAuthorisedWithExternalIdAndCredId = Action.async { implicit request =>
      authorised().retrieve(externalId and credentials) {
        case Some(id) ~ Some(cred) =>
          val json = Json.obj("externalId" -> id, "providerId" -> cred.providerId)
          Future.successful(Ok(Json.toJson(json)))
        case _ => Future.successful(NoContent)
      } recover {
        case _ => Forbidden
      }
    }
  }

  "Calling authorise()" should {
    "return 403 if the user is not authorised" in {
      when(mockAuthConnector.authorise[Unit](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception("error")))

      val response = TestController.isAuthorised(FakeRequest())
      status(response) mustBe Status.FORBIDDEN
    }

    "return 200 if the user is authorised" in {
      when(mockAuthConnector.authorise[Unit](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful({}))

      val result = TestController.isAuthorised(FakeRequest())
      status(result) mustBe OK
    }
  }

  "Calling authorise().retrieve(internalId)" should {
    "return 200 with an internalId if the user is authorised" in {
      when(mockAuthConnector.authorise[Option[String]](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some("test-internal-id")))

      val result = TestController.isAuthorisedWithData(FakeRequest())
      status(result) mustBe OK
      contentAsString(result) mustBe "test-internal-id"
    }

    "return 204 if there is no internalId and the user is authorised" in {
      when(mockAuthConnector.authorise[Option[String]](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      val result = TestController.isAuthorisedWithData(FakeRequest())
      status(result) mustBe NO_CONTENT
    }
  }

  "Calling authorise().retrieve(credentials)" should {
    "return 200 with a valid providerId" in {
      val credential = Credentials("some-provider-id", "testProviderType")
      AuthorisationMocks.mockAuthoriseTest(Future.successful(Some(credential)))

      val result = TestController.isAuthorisedWithCredId(FakeRequest())
      status(result) mustBe OK
      contentAsString(result) mustBe "some-provider-id"
    }
  }

  "Calling authorise().retrieve(externalId ~ credentials)" should {
    "return 200 with a valid externalId and providerId" in {
      val cred = Credentials("some-provider-id", "some-provider-type")

      AuthorisationMocks.mockAuthoriseTest(Future.successful(new ~(Some("some-external-id"), Some(cred))))

      val result = TestController.isAuthorisedWithExternalIdAndCredId(FakeRequest())
      status(result) mustBe OK

      contentAsJson(result) mustBe Json.obj("externalId" -> "some-external-id", "providerId" -> cred.providerId)
    }

    "return 204 if there is no externalId" in {
      val cred = Credentials("some-provider-id", "some-provider-type")

      AuthorisationMocks.mockAuthoriseTest(Future.successful(new ~(None, Some(cred))))

      val result = TestController.isAuthorisedWithExternalIdAndCredId(FakeRequest())
      status(result) mustBe NO_CONTENT
    }

    "return 403 if something failed" in {
      when(mockAuthConnector.authorise[Option[String] ~ Credentials](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception("something wrong")))

      val result = TestController.isAuthorisedWithExternalIdAndCredId(FakeRequest())
      status(result) mustBe FORBIDDEN
    }
  }
}
