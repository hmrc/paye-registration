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

package mocks

import auth.CryptoSCRS
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import repositories.{RegistrationMongoRepository, SequenceMongoRepository}
import uk.gov.hmrc.auth.core.{AuthConnector, InvalidBearerToken}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait PAYEMocks {

  this: MockitoSugar =>

  lazy val mockRegistrationRepository = mock[RegistrationMongoRepository]
  lazy val mockRegistrationMongo      = mock[RegistrationMongoRepository]
  lazy val mockSequenceRepository     = mock[SequenceMongoRepository]
  lazy val mockAuthConnector          = mock[AuthConnector]
  lazy val mockPlayConfiguraton       = mock[Configuration]
  lazy val mockCrypto                 = mock[CryptoSCRS]

  object AuthorisationMocks {
    def mockAuthenticated(internalId: String): OngoingStubbing[Future[Option[String]]] = {
      when(mockAuthConnector.authorise[Option[String]](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(internalId)))
    }

    def mockAuthoriseTest[T](returns: Future[T]): OngoingStubbing[Future[T]] = {
      when(mockAuthConnector.authorise[T](any(), any())(any(), any()))
        .thenReturn(returns)
    }

    def mockNotAuthenticated(): OngoingStubbing[Future[Option[String]]] = {
      when(mockAuthConnector.authorise[Option[String]](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new InvalidBearerToken("Invalid Bearer Token")))
    }

    def mockAuthenticatedNoInternalId(): OngoingStubbing[Future[Option[String]]] = {
      when(mockAuthConnector.authorise[Option[String]](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))
    }

    def mockAuthorised(regId: String, internalId: String): OngoingStubbing[Future[Option[String]]] = {
      when(mockAuthConnector.authorise[Option[String]](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(internalId)))

      when(mockRegistrationRepository.getInternalId(ArgumentMatchers.eq(regId))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(internalId)))
    }

    def mockNotAuthorised(regId: String, internalId: String): OngoingStubbing[Future[Option[String]]] = {
      when(mockAuthConnector.authorise[Option[String]](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(internalId)))

      when(mockRegistrationRepository.getInternalId(ArgumentMatchers.eq(regId))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(internalId + "xxx")))
    }

    def mockAuthResourceNotFound(regId: String, internalId: String): OngoingStubbing[Future[Option[String]]] = {
      when(mockAuthConnector.authorise[Option[String]](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(internalId)))

      when(mockRegistrationRepository.getInternalId(ArgumentMatchers.eq(regId))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))
    }

    def mockNotLoggedInOrAuthorised(regId: String): OngoingStubbing[Future[Option[String]]] = {
      when(mockAuthConnector.authorise[Option[String]](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      when(mockRegistrationRepository.getInternalId(ArgumentMatchers.eq(regId))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some("SomeInternalId")))
    }
  }
}
