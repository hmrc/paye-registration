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

package services

import common.exceptions.DBExceptions.MissingRegDocument
import connectors.UserDetailsModel
import enums.AddressTypes
import fixtures.{AuthFixture, RegistrationFixture}
import helpers.PAYERegSpec
import models.{Address, CompanyDetails, DigitalContactDetails}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId

class AuditServiceSpec extends PAYERegSpec with RegistrationFixture with AuthFixture {
  val mockAuditConnector = mock[AuditConnector]

  class Setup {
    val service = new AuditSrv {
      override val registrationRepository = mockRegistrationRepository
      override val auditConnector = mockAuditConnector
      override val authConnector = mockAuthConnector
    }
  }

  val regId = "AB123456"
  val authProviderId = "testAuthProviderId"
  val validUserDetailsModel = UserDetailsModel("testName", "testEmail", "testAffinityGroup", None, None, None, None, authProviderId, "testAuthProviderType")
  val validCompanyDetailsWithAuditRef = CompanyDetails(
    companyName = "Test Company Name",
    tradingName = Some("Test Trading Name"),
    Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), Some("UK"), Some("roAuditRef")),
    Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK")),
    DigitalContactDetails(Some("test@email.com"), Some("012345"), Some("543210"))
  )

  "auditCompletionCapacity" should {
    implicit val hc = HeaderCarrier(sessionId = Some(SessionId("session-123")))

    "send audit with correct detail" in new Setup {
      val previousCC = "director"
      val newCC = "agent"

      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validAuthority)))

      when(mockAuthConnector.getUserDetails(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validUserDetailsModel)))

      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(AuditResult.Success))

      await(service.auditCompletionCapacity(regId, previousCC, newCC)) shouldBe AuditResult.Success
    }
  }

  "Calling fetchAddressAuditRefs" should {
    "return a map of enums to refs" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(companyDetails = Some(validCompanyDetailsWithAuditRef)))))

      await(service.fetchAddressAuditRefs("regId")) shouldBe Map(AddressTypes.roAdddress -> "roAuditRef")
    }
    "throw a MissingRegDocument exception when there is no Registration object returned from mongo" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      intercept[MissingRegDocument](await(service.fetchAddressAuditRefs("regId")))
    }
  }
}
