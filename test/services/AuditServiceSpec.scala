/*
 * Copyright 2023 HM Revenue & Customs
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

import audit.DesTopUpAuditEventDetail
import common.exceptions.DBExceptions.MissingRegDocument
import enums.AddressTypes
import fixtures.RegistrationFixture
import helpers.PAYERegSpec
import models.{Address, CompanyDetails, DigitalContactDetails}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.config.AuditingConfig
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class AuditServiceSpec extends PAYERegSpec with RegistrationFixture {
  val mockAuditConnector = mock[AuditConnector]

  class Setup(otherHcHeaders: Seq[(String, String)] = Seq()) {

    implicit val hc = HeaderCarrier(otherHeaders = otherHcHeaders)
    implicit val ec = ExecutionContext.global

    val mockAuditConnector = mock[AuditConnector]
    val mockAuditingConfig = mock[AuditingConfig]

    val instantNow = Instant.now()
    val appName = "business-registration-notification"
    val auditType = "testAudit"
    val testEventId = UUID.randomUUID().toString
    val txnName = "transactionName"

    when(mockAuditConnector.auditingConfig) thenReturn mockAuditingConfig
    when(mockAuditingConfig.auditSource) thenReturn appName

    val event = DesTopUpAuditEventDetail(regId = "regId", jsSubmission = Json.obj("submissionId" -> "123456"))

    val service = new AuditService(mockRegistrationRepository, mockAuthConnector, mockAuditConnector) {
      override private[services] def now() = instantNow
      override private[services] def eventId() = testEventId
    }
  }

  val regId = "AB123456"
  val credentials: Credentials = Credentials("cred-123", "testProviderType")
  val providerId = "cred-123"

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

      AuthorisationMocks.mockAuthoriseTest(Future.successful(new ~(Some("some-external-id"), Some(credentials))))

      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(AuditResult.Success))

      await(service.auditCompletionCapacity(regId, previousCC, newCC)) mustBe AuditResult.Success
    }
  }

  "Calling fetchAddressAuditRefs" should {
    "return a map of enums to refs" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(companyDetails = Some(validCompanyDetailsWithAuditRef)))))

      await(service.fetchAddressAuditRefs("regId")) mustBe Map(AddressTypes.roAdddress -> "roAuditRef")
    }
    "throw a MissingRegDocument exception when there is no Registration object returned from mongo" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      intercept[MissingRegDocument](await(service.fetchAddressAuditRefs("regId")))
    }
  }

  ".sendEvent" when {

    "call to AuditConnector is successful" when {

      "transactionName is provided and path does NOT exist" must {

        "create and send an Explicit ExtendedAuditEvent including the transactionName with pathTag set to '-'" in new Setup {

          when(
            mockAuditConnector.sendExtendedEvent(
              ArgumentMatchers.eq(ExtendedDataEvent(
                auditSource = appName,
                auditType = auditType,
                eventId = testEventId,
                tags = hc.toAuditTags(txnName, "-"),
                detail = Json.toJson(event),
                generatedAt = instantNow
              ))
            )(
              ArgumentMatchers.eq(hc),
              ArgumentMatchers.eq(ec)
            )
          ) thenReturn Future.successful(AuditResult.Success)

          val actual = await(service.sendEvent(auditType, event, Some(txnName)))

          actual mustBe AuditResult.Success
        }
      }

      "transactionName is NOT provided and path exists" must {

        "create and send an Explicit ExtendedAuditEvent with transactionName as auditType & pathTag extracted from the HC" in new Setup(
          otherHcHeaders = Seq("path" -> "/wizz/foo/bar")
        ) {

          when(
            mockAuditConnector.sendExtendedEvent(
              ArgumentMatchers.eq(ExtendedDataEvent(
                auditSource = appName,
                auditType = auditType,
                eventId = testEventId,
                tags = hc.toAuditTags(auditType, "/wizz/foo/bar"),
                detail = Json.toJson(event),
                generatedAt = instantNow
              ))
            )
            (
              ArgumentMatchers.eq(hc),
              ArgumentMatchers.eq(ec)
            )
          ) thenReturn Future.successful(AuditResult.Success)

          val actual = await(service.sendEvent(auditType, event, None))

          actual mustBe AuditResult.Success
        }
      }
    }

    "call to AuditConnector fails" must {

      "throw the exception" in new Setup {

        val exception = new Exception("Oh No")

        when(
          mockAuditConnector.sendExtendedEvent(
            ArgumentMatchers.eq(ExtendedDataEvent(
              auditSource = appName,
              auditType = auditType,
              eventId = testEventId,
              tags = hc.toAuditTags(txnName, "-"),
              detail = Json.toJson(event),
              generatedAt = instantNow
            ))
          )
          (
            ArgumentMatchers.eq(hc),
            ArgumentMatchers.eq(ec)
          )
        ) thenReturn Future.failed(exception)

        val actual = intercept[Exception](await(service.sendEvent(auditType, event, Some(txnName))))

        actual.getMessage mustBe exception.getMessage
      }
    }
  }
}
