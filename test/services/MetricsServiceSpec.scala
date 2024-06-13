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

package services

import helpers.PAYERegSpec
import mocks.MetricsMock
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MetricsServiceSpec extends PAYERegSpec with BeforeAndAfterEach {

  trait Setup {
    val service = MetricsMock.mockMetricsService
  }

  "Metrics" should {
    "update no metrics if no registration stats" in new Setup() {
      when(service.regRepo.getRegistrationStats())
        .thenReturn(Future.successful(Map[String, Int]()))

      val result: Map[String, Int] = await(service.updateDocumentMetrics())

      result mustBe Map()

    }

    "update a single metric when one is supplied" in new Setup() {
      when(service.regRepo.getRegistrationStats())
        .thenReturn(Future.successful(Map[String, Int]("test" -> 1)))

      await(service.updateDocumentMetrics()) mustBe Map("test" -> 1)
    }

    "update multiple metrics when required" in new Setup() {
      when(service.regRepo.getRegistrationStats())
        .thenReturn(Future.successful(Map[String, Int]("testOne" -> 1, "testTwo" -> 2, "testThree" -> 3)))

      val result = await(service.updateDocumentMetrics())

      result mustBe Map("testOne" -> 1, "testTwo" -> 2, "testThree" -> 3)
    }
  }
}
