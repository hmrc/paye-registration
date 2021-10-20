/*
 * Copyright 2021 HM Revenue & Customs
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

package Jobs

import config.StartUpJobs
import enums.{Employing, PAYEStatus}
import helpers.{LogCapturing, PAYERegSpec}
import models._
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import play.api.Logger
import utils.SystemDate

import java.time.{LocalDateTime, ZoneOffset, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RetrieveRegIdFromTxIDJobSpec extends PAYERegSpec with LogCapturing with Eventually {

  class Setup {
    val job = () => new StartUpJobs(mockRegistrationMongo, mockPlayConfiguraton)
  }

  val timestamp = "2017-05-09T07:58:35.000Z"
  val zDtNow = ZonedDateTime.of(LocalDateTime.of(2000,1,20,16,0),ZoneOffset.UTC)

  val tstPAYERegistration = PAYERegistration(
    registrationID = "12345",
    transactionID = "NNASD9789F",
    internalID = "09876",
    acknowledgementReference = None,
    crn = None,
    formCreationTimestamp = "2016-05-31",
    registrationConfirmation = Some(EmpRefNotification(
      empRef = Some("testEmpRef"),
      timestamp = "2017-01-01T12:00:00Z",
      status = "testStatus"
    )),
    status = PAYEStatus.draft,
    completionCapacity = Some("Director"),
    companyDetails = Some(
      CompanyDetails(
        companyName = "Test Company",
        tradingName = Some("Test Trading Name"),
        Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), None, Some("testAudit")),
        Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), None, Some("UK")),
        DigitalContactDetails(Some("email@test.co.uk"), Some("9999999999"), Some("0000000000"))
      )
    ),
    directors = Seq(
      Director(
        Name(
          forename = Some("Thierry"),
          otherForenames = Some("Dominique"),
          surname = Some("Henry"),
          title = Some("Sir")
        ),
        Some("SR123456C")
      ),
      Director(
        Name(
          forename = Some("David"),
          otherForenames = Some("Jesus"),
          surname = Some("Trezeguet"),
          title = Some("Mr")
        ),
        Some("SR000009C")
      )
    ),
    payeContact = Some(
      PAYEContact(
        contactDetails = PAYEContactDetails(
          name = "toto tata",
          digitalContactDetails = DigitalContactDetails(
            Some("payeemail@test.co.uk"),
            Some("6549999999"),
            Some("1234599999")
          )
        ),
        correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), None, Some("UK"))
      )
    ),

    sicCodes = Seq(
      SICCode(code = Some("666"), description = Some("demolition")),
      SICCode(code = None, description = Some("laundring"))
    ),
    lastUpdate = timestamp,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(zDtNow),
    employmentInfo = Some(EmploymentInfo(Employing.notEmploying,SystemDate.getSystemDate.toLocalDate,true, true,None))
  )

  "logRegIdFromTxId" should {
    "log a single txId with its regId when present" in new Setup {
      when(mockPlayConfiguraton.getOptional[String](eqTo("txIdListToRegIdForStartupJob"))(any()))
        .thenReturn(Some("dHhJZDE="))

      when(mockRegistrationMongo.retrieveRegistrationByTransactionID(any()))
        .thenReturn(Future.successful(Some(tstPAYERegistration.copy(registrationID = "regIdX"))))

      withCaptureOfLoggingFrom(Logger) { logs =>
        job()
        eventually(logs.exists(
          _.getMessage == s"[RetrieveRegInfoFromTxIdJob] txId: txId1 returned a document with regId: regIdX, status: draft, lastUpdated: $timestamp and lastAction: ${Some(zDtNow)}") shouldBe true)
        eventually(logs.size shouldBe 1)
      }
    }

    "log all txId with their regIds when present" in new Setup {
      when(mockPlayConfiguraton.getOptional[String](eqTo("txIdListToRegIdForStartupJob"))(any()))
        .thenReturn(Some("dHhJZDEsdHhJZDIsdHhJZDM="))

      when(mockRegistrationMongo.retrieveRegistrationByTransactionID(any()))
        .thenReturn(
          Future.successful(Some(tstPAYERegistration.copy(registrationID = "regId1"))),
          Future.successful(Some(tstPAYERegistration.copy(registrationID = "regId2"))),
          Future.successful(Some(tstPAYERegistration.copy(registrationID = "regId3")))
        )

      withCaptureOfLoggingFrom(Logger) { logs =>
        job()
        eventually(logs.size shouldBe 3)
        eventually(logs.exists(
          _.getMessage == s"[RetrieveRegInfoFromTxIdJob] txId: txId3 returned a document with regId: regId3, status: draft, lastUpdated: $timestamp and lastAction: ${Some(zDtNow)}") shouldBe true)
        eventually(logs.exists(
          _.getMessage == s"[RetrieveRegInfoFromTxIdJob] txId: txId2 returned a document with regId: regId2, status: draft, lastUpdated: $timestamp and lastAction: ${Some(zDtNow)}") shouldBe true)
        eventually(logs.exists(
          _.getMessage == s"[RetrieveRegInfoFromTxIdJob] txId: txId1 returned a document with regId: regId1, status: draft, lastUpdated: $timestamp and lastAction: ${Some(zDtNow)}") shouldBe true)
      }
    }

    "not stop logging if one txId doesn't have a document" in new Setup {
      when(mockPlayConfiguraton.getOptional[String](eqTo("txIdListToRegIdForStartupJob"))(any()))
        .thenReturn(Some("dHhJZDEsdHhJZDIsdHhJZDM="))

      when(mockRegistrationMongo.retrieveRegistrationByTransactionID(any()))
        .thenReturn(
          Future.successful(Some(tstPAYERegistration.copy(registrationID = "regId1"))),
          Future.successful(None),
          Future.successful(Some(tstPAYERegistration.copy(registrationID = "regId3")))
        )

      withCaptureOfLoggingFrom(Logger) { logs =>
        job()
        eventually(logs.size shouldBe 3)
        eventually(logs.exists(
          _.getMessage == s"[RetrieveRegInfoFromTxIdJob] txId: txId3 returned a document with regId: regId3, status: draft, lastUpdated: $timestamp and lastAction: ${Some(zDtNow)}") shouldBe true)
        eventually(logs.exists(_.getMessage == "[RetrieveRegInfoFromTxIdJob] txId: txId2 has no registration document") shouldBe true)
        eventually(logs.exists(
          _.getMessage == s"[RetrieveRegInfoFromTxIdJob] txId: txId1 returned a document with regId: regId1, status: draft, lastUpdated: $timestamp and lastAction: ${Some(zDtNow)}") shouldBe true)
      }
    }

    "not stop logging if all txids throw an exception" in new Setup {
      when(mockPlayConfiguraton.getOptional[String](eqTo("txIdListToRegIdForStartupJob"))(any()))
        .thenReturn(Some("dHhJZDEsdHhJZDIsdHhJZDM="))

      when(mockRegistrationMongo.retrieveRegistrationByTransactionID(any()))
        .thenReturn(Future.failed(new Exception("Something Went Wrong regId1")), Future.successful(None), Future.failed(new Exception("Something Went Wrong regId3")))

      withCaptureOfLoggingFrom(Logger) { logs =>
        job()
        eventually(logs.size shouldBe 3)
        eventually(logs.exists(_.getMessage == "[RetrieveRegInfoFromTxIdJob] an error occurred while retrieving regId for txId: txId1") shouldBe true)
        eventually(logs.exists(_.getMessage == "[RetrieveRegInfoFromTxIdJob] txId: txId2 has no registration document") shouldBe true)
        eventually(logs.exists(_.getMessage == "[RetrieveRegInfoFromTxIdJob] an error occurred while retrieving regId for txId: txId3") shouldBe true)
      }
    }

    "not log anything if there is no txid list passed in" in new Setup {
      when(mockPlayConfiguraton.getOptional[String](eqTo("txIdListToRegIdForStartupJob"))(any()))
        .thenReturn(None)

      withCaptureOfLoggingFrom(Logger) { logs =>
        job().logRegInfoFromTxId()
        eventually(logs.size shouldBe 0)
      }
    }
  }
}
