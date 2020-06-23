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

import enums.PAYEStatus
import fixtures.RegistrationFixture
import helpers.PAYERegSpec
import models.EmpRefNotification
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.test.Helpers._
import repositories.RegistrationMongoRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NotificationServiceSpec extends PAYERegSpec with RegistrationFixture {

  class Setup {
    val testService = new NotificationService(mockRegistrationRepository)
  }

  "getNewApplicationStatus" should {
    "return submitted" when {
      "the status is 04" in new Setup {
        val result = testService.getNewApplicationStatus("04")
        result shouldBe PAYEStatus.acknowledged
      }

      "the status is 05" in new Setup {
        val result = testService.getNewApplicationStatus("05")
        result shouldBe PAYEStatus.acknowledged
      }
    }

    "return rejected" when {
      "the status is 06" in new Setup {
        val result = testService.getNewApplicationStatus("06")
        result shouldBe PAYEStatus.rejected
      }

      "the status is 07" in new Setup {
        val result = testService.getNewApplicationStatus("07")
        result shouldBe PAYEStatus.rejected
      }

      "the status is 08" in new Setup {
        val result = testService.getNewApplicationStatus("08")
        result shouldBe PAYEStatus.rejected
      }

      "the status is 09" in new Setup {
        val result = testService.getNewApplicationStatus("09")
        result shouldBe PAYEStatus.rejected
      }

      "the status is 10" in new Setup {
        val result = testService.getNewApplicationStatus("10")
        result shouldBe PAYEStatus.rejected
      }
    }
  }

  "processNotification" should {
    "return an EmpRefNotification" in new Setup {
      val testAckRef = "testAckRef"
      val testNotification = EmpRefNotification(Some("testEmpRef"), "testTimeStamp", "04")

      when(mockRegistrationRepository.updateRegistrationEmpRef(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(testNotification))
      when(mockRegistrationRepository.retrieveRegistrationByAckRef(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validRegistration)))
      when(mockRegistrationRepository.updateRegistrationStatus(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(PAYEStatus.acknowledged))

      val result = await(testService.processNotification(testAckRef, testNotification))
      result shouldBe testNotification
    }
  }
}
