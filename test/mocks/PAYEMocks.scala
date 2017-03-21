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

package mocks

import connectors.{AuthConnector, Authority}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.mockito.MockitoSugar
import repositories.{RegistrationMongoRepository, SequenceMongoRepository}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

trait PAYEMocks {

  this:MockitoSugar =>

  lazy val mockAuthConnector = mock[AuthConnector]
  lazy val mockRegistrationRepository = mock[RegistrationMongoRepository]
  lazy val mockSequenceRepository = mock[SequenceMongoRepository]

  object AuthenticationMocks {
    def getCurrentAuthority(authority: Option[Authority]): OngoingStubbing[Future[Option[Authority]]] = {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(authority))
    }
  }

  object AuthorisationMocks {

    def mockSuccessfulAuthorisation(registrationId: String, authority: Authority) = {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(authority)))
    }

    def mockNotLoggedInOrAuthorised = {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))
    }

    def mockNotAuthorised(registrationId: String, authority: Authority) = {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(authority)))
    }

    def getCurrentAuthority(authority: Option[Authority]): OngoingStubbing[Future[Option[Authority]]] = {
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(authority))
    }

    def getInternalId(intId: String, thenReturn: Option[(String, String)]): OngoingStubbing[Future[Option[(String, String)]]] = {
      when(mockRegistrationRepository.getInternalId(ArgumentMatchers.eq(intId))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(thenReturn))
    }
  }
}
