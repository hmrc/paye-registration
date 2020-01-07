/*
 * Copyright 2020 HM Revenue & Customs
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
import enums.AddressTypes
import fixtures.RegistrationFixture
import helpers.PAYERegSpec
import models.{Address, CompanyDetails, DigitalContactDetails}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuditServiceSpec extends PAYERegSpec with RegistrationFixture {
  val mockAuditConnector = mock[AuditConnector]

  class Setup {
    val service = new AuditSrv {
      override lazy val authConnector = mockAuthConnector

      override val registrationRepository = mockRegistrationRepository
      override val auditConnector = mockAuditConnector
    }
  }

  val regId = "AB123456"
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
      val cred = Credentials("cred-123", "some-provider-type")

      when(mockAuthConnector.authorise[Option[String] ~ Credentials](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(new ~(Some("some-external-id"), cred)))

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
