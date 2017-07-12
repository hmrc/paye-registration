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

package controllers.test

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.test.FakeRequest
import play.api.test.Helpers.{BAD_REQUEST, OK}
import helpers.PAYERegSpec
import utils.{BooleanFeatureSwitch, PAYEFeatureSwitches}

import scala.concurrent.Future
class FeatureSwitchControllerSpec extends PAYERegSpec {

  implicit val system = ActorSystem("PR")
  implicit val materializer = ActorMaterializer()
  override def beforeEach(): Unit = {
    System.clearProperty("feature.desServiceFeature")
  }
  class Setup {
    val controller = new FeatureSwitchCtrl {
    }
  }
  val testFeatureSwitch = BooleanFeatureSwitch(name = "desServiceFeature", enabled = true)
  val testDisabledSwitch = BooleanFeatureSwitch(name = "desServiceFeature", enabled = false)

  "switch" should {
    "enable the desServiceFeature and return an OK" when {
      "desStubFeature and true are passed in the url" in new Setup {
        val result = controller.switch("desServiceFeature","true")(FakeRequest())
        status(result) shouldBe OK
        bodyOf(await(result)) shouldBe testFeatureSwitch.toString
      }
    }

    "disable the desServiceFeature and return an OK" when {
      "desStubFeature and some other featureState is passed into the URL" in new Setup {
        val result = await(controller.switch("desServiceFeature","someOtherState")(FakeRequest()))
        status(result) shouldBe OK
        bodyOf(await(result)) shouldBe testDisabledSwitch.toString
      }
    }

    "return a bad request" when {
      "an unknown feature is trying to be enabled" in new Setup {

        val result = controller.switch("invalidName","invalidState")(FakeRequest())
        status(result) shouldBe BAD_REQUEST
      }
    }
  }
}
