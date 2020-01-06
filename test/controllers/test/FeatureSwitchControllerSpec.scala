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

package controllers.test

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import helpers.PAYERegSpec
import jobs.ScheduledJob
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.test.FakeRequest
import play.api.test.Helpers.{BAD_REQUEST, OK}
import utils.BooleanFeatureSwitch

class FeatureSwitchControllerSpec extends PAYERegSpec {

  implicit val system = ActorSystem("PR")
  implicit val materializer = ActorMaterializer()
  val mockRemoveStaleDocsJob: ScheduledJob = mock[ScheduledJob]
  val mockGraphiteMetrics: ScheduledJob = mock[ScheduledJob]
  val mockQuartz = mock[QuartzSchedulerExtension]


  override def beforeEach(): Unit = {
    System.clearProperty("feature.desServiceFeature")
  }

  class Setup {
    val controller = new FeatureSwitchCtrl {
      val removeStaleDocsJob = mockRemoveStaleDocsJob
      val graphiteMetrics = mockGraphiteMetrics

    }
  }

  val testFeatureSwitch = BooleanFeatureSwitch(name = "desServiceFeature", enabled = true)
  val testDisabledSwitch = BooleanFeatureSwitch(name = "desServiceFeature", enabled = false)
  val testRemoveStaleDocFeatureSwitchTrue = BooleanFeatureSwitch(name = "removeStaleDocumentsFeature", enabled = true)
  val testRemoveStaleDocFeatureSwitchFalse = BooleanFeatureSwitch(name = "removeStaleDocumentsFeature", enabled = false)
  val testGraphiteMetricsFeatureSwitchTrue = BooleanFeatureSwitch(name = "graphiteMetrics", enabled = true)
  val testGraphiteMetricsFeatureSwitchFalse = BooleanFeatureSwitch(name = "graphiteMetrics", enabled = false)

  "switch" should {
    "enable a ServiceFeature and return an OK" when {
      "desStubFeature and true are passed in the url" in new Setup {
        val result = controller.switch("desServiceFeature", "true")(FakeRequest())
        status(result) shouldBe OK
        bodyOf(await(result)) shouldBe testFeatureSwitch.toString
      }

      "removeStaleDocumentsFeature and true is passed in the url" in new Setup {
        when(mockRemoveStaleDocsJob.scheduler).thenReturn(mockQuartz)
        when(mockQuartz.resumeJob(any())).thenReturn(true)

        val result = controller.switch("removeStaleDocumentsFeature", "true")(FakeRequest())

        status(result) shouldBe OK
        bodyOf(await(result)) shouldBe testRemoveStaleDocFeatureSwitchTrue.toString
      }

      "removeStaleDocumentsFeature and false is passed in the url" in new Setup {
        when(mockRemoveStaleDocsJob.scheduler).thenReturn(mockQuartz)
        when(mockQuartz.suspendJob(any())).thenReturn(false)

        val result = controller.switch("removeStaleDocumentsFeature", "false")(FakeRequest())

        status(result) shouldBe OK
        bodyOf(await(result)) shouldBe testRemoveStaleDocFeatureSwitchFalse.toString
      }

      "graphiteMetrics and true is passed in the url" in new Setup {
        when(mockGraphiteMetrics.scheduler).thenReturn(mockQuartz)
        when(mockQuartz.resumeJob(any())).thenReturn(true)

        val result = controller.switch("graphiteMetrics", "true")(FakeRequest())

        status(result) shouldBe OK
        bodyOf(await(result)) shouldBe testGraphiteMetricsFeatureSwitchTrue.toString
      }

      "graphiteMetrics and false is passed in the url" in new Setup {
        when(mockGraphiteMetrics.scheduler).thenReturn(mockQuartz)
        when(mockQuartz.suspendJob(any())).thenReturn(false)

        val result = controller.switch("graphiteMetrics", "false")(FakeRequest())

        status(result) shouldBe OK
        bodyOf(await(result)) shouldBe testGraphiteMetricsFeatureSwitchFalse.toString
      }
    }

    "disable the desServiceFeature and return an OK" when {
      "desStubFeature and some other featureState is passed into the URL" in new Setup {
        val result = await(controller.switch("desServiceFeature", "someOtherState")(FakeRequest()))
        status(result) shouldBe OK
        bodyOf(await(result)) shouldBe testDisabledSwitch.toString
      }
    }

    "return a bad request" when {
      "an unknown feature is trying to be enabled" in new Setup {

        val result = controller.switch("invalidName", "invalidState")(FakeRequest())
        status(result) shouldBe BAD_REQUEST
      }
    }
  }
}
