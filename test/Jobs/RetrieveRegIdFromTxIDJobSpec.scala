/*
 * Copyright 2018 HM Revenue & Customs
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

import common.exceptions.DBExceptions.MissingRegDocument
import helpers.PAYERegSpec
import jobs.RetrieveRegIdFromTxIdJob
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import play.api.Logger
import uk.gov.hmrc.play.test.LogCapturing

import scala.concurrent.Future

class RetrieveRegIdFromTxIDJobSpec extends PAYERegSpec with LogCapturing with Eventually {

  class Setup {
    val job = new RetrieveRegIdFromTxIdJob(mockRegistrationRepository, mockPlayConfiguraton)
  }

  "logRegIdFromTxId" should {
    "log a single txId with its regId when present" in new Setup {
      when(mockPlayConfiguraton.getString(eqTo("txIdListToRegIdForStartupJob"), any()))
        .thenReturn(Some("dHhJZDE="))

      when(mockRegistrationRepository.getRegistrationId(any())(any()))
        .thenReturn(Future.successful("regIdX"))

      withCaptureOfLoggingFrom(Logger) { logs =>
        job.logRegIdsFromTxId()
        eventually(logs.exists(_.getMessage == "[RetrieveRegIdFromTxIdJob] txId: txId1 returned a regId: regIdX") shouldBe true)
        eventually(logs.size shouldBe 1)
      }
    }

    "log all txId with their regIds when present" in new Setup {
      when(mockPlayConfiguraton.getString(eqTo("txIdListToRegIdForStartupJob"), any()))
        .thenReturn(Some("dHhJZDEsdHhJZDIsdHhJZDM="))

      when(mockRegistrationRepository.getRegistrationId(any())(any()))
        .thenReturn(Future.successful("regId1"), Future.successful("regId2"), Future.successful("regId3"))

      withCaptureOfLoggingFrom(Logger) { logs =>
        job.logRegIdsFromTxId()
        eventually(logs.size shouldBe 3)
        eventually(logs.exists(_.getMessage == "[RetrieveRegIdFromTxIdJob] txId: txId1 returned a regId: regId1") shouldBe true)
        eventually(logs.exists(_.getMessage == "[RetrieveRegIdFromTxIdJob] txId: txId2 returned a regId: regId2") shouldBe true)
        eventually(logs.exists(_.getMessage == "[RetrieveRegIdFromTxIdJob] txId: txId3 returned a regId: regId3") shouldBe true)
      }
    }

    "not stop logging if one txId doesn't have a document" in new Setup {
      when(mockPlayConfiguraton.getString(eqTo("txIdListToRegIdForStartupJob"), any()))
        .thenReturn(Some("dHhJZDEsdHhJZDIsdHhJZDM="))

      when(mockRegistrationRepository.getRegistrationId(any())(any()))
        .thenReturn(Future.successful("regId1"), Future.failed(new MissingRegDocument("regId2")), Future.successful("regId3"))

      withCaptureOfLoggingFrom(Logger) { logs =>
        job.logRegIdsFromTxId()
        eventually(logs.size shouldBe 3)
        eventually(logs.exists(_.getMessage == "[RetrieveRegIdFromTxIdJob] txId: txId1 returned a regId: regId1") shouldBe true)
        eventually(logs.exists(_.getMessage == "[RetrieveRegIdFromTxIdJob] txId: txId2 has no registration document") shouldBe true)
        eventually(logs.exists(_.getMessage == "[RetrieveRegIdFromTxIdJob] txId: txId3 returned a regId: regId3") shouldBe true)
      }
    }

    "not stop logging if all txids throw an exception" in new Setup {
      when(mockPlayConfiguraton.getString(eqTo("txIdListToRegIdForStartupJob"), any()))
        .thenReturn(Some("dHhJZDEsdHhJZDIsdHhJZDM="))

      when(mockRegistrationRepository.getRegistrationId(any())(any()))
        .thenReturn(Future.failed(new Exception("Something Went Wrong regId1")), Future.failed(new MissingRegDocument("regId2")), Future.failed(new Exception("Something Went Wrong regId3")))

      withCaptureOfLoggingFrom(Logger) { logs =>
        job.logRegIdsFromTxId()
        eventually(logs.size shouldBe 3)
        eventually(logs.exists(_.getMessage == "[RetrieveRegIdFromTxIdJob] an error occurred while retrieving regId for txId: txId1") shouldBe true)
        eventually(logs.exists(_.getMessage == "[RetrieveRegIdFromTxIdJob] txId: txId2 has no registration document") shouldBe true)
        eventually(logs.exists(_.getMessage == "[RetrieveRegIdFromTxIdJob] an error occurred while retrieving regId for txId: txId3") shouldBe true)
      }
    }

    "not log anything if there is no txid list passed in" in new Setup {
      when(mockPlayConfiguraton.getString(eqTo("txIdListToRegIdForStartupJob"), any()))
        .thenReturn(None)

      withCaptureOfLoggingFrom(Logger) { logs =>
        job.logRegIdsFromTxId()
        eventually(logs.size shouldBe 0)
      }
    }
  }
}
