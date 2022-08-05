/*
 * Copyright 2022 HM Revenue & Customs
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

package Connectors

import config.AppConfig
import connectors.DESConnector
import fixtures.SubmissionFixture
import helpers.PAYERegSpec
import models.submission.{DESSubmission, TopUpDESSubmission}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfter
import play.api.libs.json.Writes
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{LocalDate, LocalTime}
import scala.concurrent.Future

class DesConnectorSpec extends PAYERegSpec with BeforeAndAfter with SubmissionFixture {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val mockHttp: HttpClient = mock[HttpClient]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  class SetupWithProxy(withProxy: Boolean) {

    object MockAppConfig extends AppConfig(mock[ServicesConfig]) {
      override lazy val desURI = "testURI"
      override lazy val desTopUpURI = "desTopUpURI"
      override lazy val desUrl = "desURL"
      override lazy val desStubTopUpURI = "desTopUpURI"
      override lazy val desStubURI = "testStubURI"
      override lazy val desStubUrl = "desStubURL"
      override lazy val desUrlHeaderEnvironment = "env"
      override lazy val desUrlHeaderAuthorization = "auth"
      override lazy val alertWorkingHours: String = "08:00:00_17:00:00"
    }

    object Connector extends DESConnector(mockHttp, MockAppConfig, mockAuditConnector) {
      override def useDESStubFeature = withProxy

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
      mockHttpPOST[DESSubmission, HttpResponse](s"${MockAppConfig.desStubUrl}/${MockAppConfig.desStubURI}", HttpResponse(200))
      await(Connector.submitToDES(validPartialDESSubmissionModel, "testRegId", Some(incorpStatusUpdate))).status shouldBe 200
    }
    "throw exception if a 400 is encountered" in new SetupWithProxy(true) {
      mockHttpFailedPOST[DESSubmission, HttpResponse](s"${MockAppConfig.desStubUrl}/${MockAppConfig.desStubURI}", Upstream4xxResponse("OOPS", 400, 400))

      intercept[Upstream4xxResponse](await(Connector.submitToDES(validPartialDESSubmissionModel, "testRegId", Some(incorpStatusUpdate))))
    }

  }

  "submitToDES with a Top Up DES Submission Model (submitTopUpToDES)" should {
    "successfully POST with proxy" in new SetupWithProxy(true) {
      mockHttpPOST[TopUpDESSubmission, HttpResponse](s"${MockAppConfig.desStubUrl}/${MockAppConfig.desStubTopUpURI}", HttpResponse(200))

      await(Connector.submitTopUpToDES(validTopUpDESSubmissionModel, "testRegId", incorpStatusUpdate.transactionId)).status shouldBe 200
    }

    "throw exception if a 400 is encountered with proxy" in new SetupWithProxy(true) {
      mockHttpFailedPOST[TopUpDESSubmission, HttpResponse](s"${MockAppConfig.desStubUrl}/${MockAppConfig.desStubTopUpURI}", Upstream4xxResponse("OOPS", 400, 400))

      intercept[Upstream4xxResponse](await(Connector.submitTopUpToDES(validTopUpDESSubmissionModel, "testRegId", incorpStatusUpdate.transactionId)))
    }

  }

  "submitToDES with a Partial DES Submission Model - feature switch disabled (submitToDES)" should {
    "successfully POST" in new SetupWithProxy(false) {
      mockHttpPOST[DESSubmission, HttpResponse](s"${MockAppConfig.desUrl}/${MockAppConfig.desURI}", HttpResponse(200))

      await(Connector.submitToDES(validPartialDESSubmissionModel, "testRegId", Some(incorpStatusUpdate))).status shouldBe 200
    }
    "throw exception if a 400 is encountered" in new SetupWithProxy(true) {
      mockHttpFailedPOST[DESSubmission, HttpResponse](s"${MockAppConfig.desUrl}/${MockAppConfig.desURI}", Upstream4xxResponse("OOPS", 400, 400))

      intercept[Upstream4xxResponse](await(Connector.submitToDES(validPartialDESSubmissionModel, "testRegId", Some(incorpStatusUpdate))))
    }
  }

  "submitToDES with a Top Up DES Submission Model - feature switch disabled" should {
    "successfully POST" in new SetupWithProxy(false) {
      mockHttpPOST[TopUpDESSubmission, HttpResponse](s"${MockAppConfig.desUrl}/${MockAppConfig.desTopUpURI}", HttpResponse(200))

      await(Connector.submitTopUpToDES(validTopUpDESSubmissionModel, "testRegId", incorpStatusUpdate.transactionId)).status shouldBe 200
    }
    "throw exception if a 400 is encountered" in new SetupWithProxy(true) {
      mockHttpFailedPOST[TopUpDESSubmission, HttpResponse](s"${MockAppConfig.desUrl}/${MockAppConfig.desTopUpURI}", Upstream4xxResponse("OOPS", 400, 400))

      intercept[Upstream4xxResponse](await(Connector.submitTopUpToDES(validTopUpDESSubmissionModel, "testRegId", incorpStatusUpdate.transactionId)))
    }
  }
}
