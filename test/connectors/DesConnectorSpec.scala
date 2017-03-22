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

package connectors

import fixtures.SubmissionFixture
import models.submission.DESSubmission
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfter
import play.api.libs.json.Writes
import helpers.PAYERegSpec
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.http._
import utils.{PAYEFeatureSwitch, PAYEFeatureSwitches}

import scala.concurrent.Future

class DesConnectorSpec extends PAYERegSpec with BeforeAndAfter with SubmissionFixture {

  implicit val hc = HeaderCarrier()
  val mockHttp = mock[WSHttp]
  val mockFeatureSwitch = mock[PAYEFeatureSwitch]

  class SetupWithProxy(withProxy: Boolean) {
    val connector = new DESConnect {
      override val featureSwitch = mockFeatureSwitch
      override def useDESStubFeature = withProxy
      override def http = mockHttp
      override val desURI = "testURI"
      override val desUrl = "desURL"
      override val desStubURI = "testStubURI"
      override val desStubUrl = "desStubURL"
    }
  }

  def mockHttpPOST[I, O](url: String, thenReturn: O, mockWSHttp: WSHttp): OngoingStubbing[Future[O]] = {
    when(mockWSHttp.POST[I, O](ArgumentMatchers.contains(url), ArgumentMatchers.any[I](), ArgumentMatchers.any())
      (ArgumentMatchers.any[Writes[I]](), ArgumentMatchers.any[HttpReads[O]](), ArgumentMatchers.any[HeaderCarrier]()))
      .thenReturn(Future.successful(thenReturn))
  }

  "submitToDES with a Partial DES Submission Model" should {
    "successfully POST with proxy" in new SetupWithProxy(true) {
      mockHttpPOST[DESSubmission, HttpResponse](s"${connector.desStubUrl}/${connector.desStubURI}", HttpResponse(200), mockHttp)

      await(connector.submitToDES(validPartialDESSubmissionModel)).status shouldBe 200
    }
  }

  "submitToDES with a Top Up DES Submission Model" should {
    "successfully POST with proxy" in new SetupWithProxy(true) {
      mockHttpPOST[DESSubmission, HttpResponse](s"${connector.desStubUrl}/${connector.desStubURI}", HttpResponse(200), mockHttp)

      await(connector.submitToDES(validTopUpDESSubmissionModel)).status shouldBe 200
    }
  }

  "submitToDES with a Partial DES Submission Model - feature switch disabled" should {
    "successfully POST with proxy" in new SetupWithProxy(false) {
      mockHttpPOST[DESSubmission, HttpResponse](s"${connector.desUrl}/${connector.desURI}", HttpResponse(200), mockHttp)

      await(connector.submitToDES(validPartialDESSubmissionModel)).status shouldBe 200
    }
  }

  "submitToDES with a Top Up DES Submission Model - feature switch disabled" should {
    "successfully POST with proxy" in new SetupWithProxy(false) {
      mockHttpPOST[DESSubmission, HttpResponse](s"${connector.desUrl}/${connector.desURI}", HttpResponse(200), mockHttp)

      await(connector.submitToDES(validTopUpDESSubmissionModel)).status shouldBe 200
    }
  }
}
