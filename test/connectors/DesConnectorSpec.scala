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

package connectors

import java.time.{LocalDate, LocalTime}

import fixtures.SubmissionFixture
import helpers.PAYERegSpec
import models.submission.{DESSubmission, TopUpDESSubmission}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfter
import play.api.libs.json.Writes
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.ws.WSHttp
import utils.PAYEFeatureSwitches

import scala.concurrent.Future

class DesConnectorSpec extends PAYERegSpec with BeforeAndAfter with SubmissionFixture {

  implicit val hc = HeaderCarrier()
  val mockHttp = mock[WSHttp]
  val mockFeatureSwitch = mock[PAYEFeatureSwitches]
  val mockAuditConnector= mock[AuditConnector]

  class SetupWithProxy(withProxy: Boolean) {
    val connector = new DESConnect {
      override val featureSwitch = mockFeatureSwitch
      override def useDESStubFeature = withProxy
      override def http = mockHttp
      override val desURI      = "testURI"
      override val desTopUpURI = "desTopUpURI"
      override val desUrl      = "desURL"
      override val desStubTopUpURI = "desTopUpURI"
      override val desStubURI      = "testStubURI"
      override val desStubUrl      = "desStubURL"
      override val urlHeaderEnvironment   = "env"
      override val urlHeaderAuthorization = "auth"
      override val auditConnector = mockAuditConnector
      override val alertWorkingHours: String = "08:00:00_17:00:00"
      override val currentTime: LocalTime = LocalTime.now
      override val currentDate: LocalDate = LocalDate.now
    }
  }

  def mockHttpPOST[I, O](url: String, thenReturn: O): OngoingStubbing[Future[O]] = {
    when(mockHttp.POST[I, O](ArgumentMatchers.contains(url), ArgumentMatchers.any[I](), ArgumentMatchers.any())
      (ArgumentMatchers.any[Writes[I]](), ArgumentMatchers.any[HttpReads[O]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
      .thenReturn(Future.successful(thenReturn))
  }

  def mockHttpFailedPOST[I, O](url: String, exception: Exception): OngoingStubbing[Future[O]] = {
    when(mockHttp.POST[I, O](ArgumentMatchers.anyString(), ArgumentMatchers.any[I](), ArgumentMatchers.any())(ArgumentMatchers.any[Writes[I]](), ArgumentMatchers.any[HttpReads[O]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
      .thenReturn(Future.failed(exception))
  }

  "submitToDES with a Partial DES Submission Model" should {
    "successfully POST with proxy" in new SetupWithProxy(true) {
      mockHttpPOST[DESSubmission, HttpResponse](s"${connector.desStubUrl}/${connector.desStubURI}", HttpResponse(200))
      await(connector.submitToDES(validPartialDESSubmissionModel, "testRegId", Some(incorpStatusUpdate))).status shouldBe 200
    }
    "throw exception if a 400 is encountered" in new SetupWithProxy(true) {
      mockHttpFailedPOST[DESSubmission, HttpResponse](s"${connector.desStubUrl}/${connector.desStubURI}",  Upstream4xxResponse("OOPS", 400, 400))

      intercept[Upstream4xxResponse](await(connector.submitToDES(validPartialDESSubmissionModel, "testRegId", Some(incorpStatusUpdate))))
    }

  }

  "submitToDES with a Top Up DES Submission Model (submitTopUpToDES)" should {
    "successfully POST with proxy" in new SetupWithProxy(true) {
      mockHttpPOST[TopUpDESSubmission, HttpResponse](s"${connector.desStubUrl}/${connector.desStubTopUpURI}", HttpResponse(200))

      await(connector.submitTopUpToDES(validTopUpDESSubmissionModel, "testRegId", incorpStatusUpdate.transactionId)).status shouldBe 200
    }

    "throw exception if a 400 is encountered with proxy" in new SetupWithProxy(true) {
      mockHttpFailedPOST[TopUpDESSubmission, HttpResponse](s"${connector.desStubUrl}/${connector.desStubTopUpURI}", Upstream4xxResponse("OOPS", 400, 400))

      intercept[Upstream4xxResponse](await(connector.submitTopUpToDES(validTopUpDESSubmissionModel, "testRegId", incorpStatusUpdate.transactionId)))
    }

  }

  "submitToDES with a Partial DES Submission Model - feature switch disabled (submitToDES)" should {
    "successfully POST" in new SetupWithProxy(false) {
      mockHttpPOST[DESSubmission, HttpResponse](s"${connector.desUrl}/${connector.desURI}", HttpResponse(200))

      await(connector.submitToDES(validPartialDESSubmissionModel, "testRegId", Some(incorpStatusUpdate))).status shouldBe 200
    }
    "throw exception if a 400 is encountered" in new SetupWithProxy(true) {
      mockHttpFailedPOST[DESSubmission, HttpResponse](s"${connector.desUrl}/${connector.desURI}",  Upstream4xxResponse("OOPS", 400, 400))

      intercept[Upstream4xxResponse](await(connector.submitToDES(validPartialDESSubmissionModel, "testRegId", Some(incorpStatusUpdate))))
    }
  }

  "submitToDES with a Top Up DES Submission Model - feature switch disabled" should {
    "successfully POST" in new SetupWithProxy(false) {
      mockHttpPOST[TopUpDESSubmission, HttpResponse](s"${connector.desUrl}/${connector.desTopUpURI}", HttpResponse(200))

      await(connector.submitTopUpToDES(validTopUpDESSubmissionModel, "testRegId", incorpStatusUpdate.transactionId)).status shouldBe 200
    }
    "throw exception if a 400 is encountered" in new SetupWithProxy(true) {
      mockHttpFailedPOST[TopUpDESSubmission, HttpResponse](s"${connector.desUrl}/${connector.desTopUpURI}", Upstream4xxResponse("OOPS", 400, 400))

      intercept[Upstream4xxResponse](await(connector.submitTopUpToDES(validTopUpDESSubmissionModel, "testRegId", incorpStatusUpdate.transactionId)))
    }
  }
}
