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

package connectors

import config.AppConfig
import helpers.PAYERegSpec
import models.incorporation.IncorpStatusUpdate
import models.submission.{ApiSubmission, TopUpDESSubmission}
import org.apache.pekko.util.Timeout
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.test.Helpers.await
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class RoutingConnectorSpec extends PAYERegSpec {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout: Timeout = Timeout(5.seconds)

  val mockSubmission: ApiSubmission = mock[ApiSubmission]
  val mockTopUpSubmission: TopUpDESSubmission = mock[TopUpDESSubmission]
  val mockIncorpUpdate: Option[IncorpStatusUpdate] = None
  val successResponse: HttpResponse = HttpResponse(200, "")

  class SetupWithHip(hipEnabled: Boolean) {
    val mockDESConnector: DESConnector = mock[DESConnector]
    val mockHIPConnector: HIPConnector = mock[HIPConnector]

    object MockAppConfig extends AppConfig(mock[ServicesConfig]) {
      override lazy val useHip: Boolean = hipEnabled
    }

    val connector = new RoutingConnector(MockAppConfig, mockDESConnector, mockHIPConnector)
  }

  "submitToEtmp" should {
    "call HIP connector when useHip is true" in new SetupWithHip(true) {
      when(mockHIPConnector.submitToHIP(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(successResponse))

      await(connector.submitToEtmp(mockSubmission, "testRegId", mockIncorpUpdate))

      verify(mockHIPConnector, times(1)).submitToHIP(any(), any(), any())(any(), any())
      verify(mockDESConnector, never()).submitToDES(any(), any(), any())(any(), any())
    }

    "call DES connector when useHip is false" in new SetupWithHip(false) {
      when(mockDESConnector.submitToDES(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(successResponse))

      await(connector.submitToEtmp(mockSubmission, "testRegId", mockIncorpUpdate))

      verify(mockDESConnector, times(1)).submitToDES(any(), any(), any())(any(), any())
      verify(mockHIPConnector, never()).submitToHIP(any(), any(), any())(any(), any())
    }
  }

  "submitTopUpToEtmp" should {
    "call HIP connector when useHip is true" in new SetupWithHip(true) {
      when(mockHIPConnector.submitTopUpToHIP(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(successResponse))

      await(connector.submitTopUpToEtmp(mockTopUpSubmission, "testRegId", "txId"))

      verify(mockHIPConnector, times(1)).submitTopUpToHIP(any(), any(), any())(any(), any())
      verify(mockDESConnector, never()).submitTopUpToDES(any(), any(), any())(any(), any())
    }

    "call DES connector when useHip is false" in new SetupWithHip(false) {
      when(mockDESConnector.submitTopUpToDES(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(successResponse))

      await(connector.submitTopUpToEtmp(mockTopUpSubmission, "testRegId", "txId"))

      verify(mockDESConnector, times(1)).submitTopUpToDES(any(), any(), any())(any(), any())
      verify(mockHIPConnector, never()).submitTopUpToHIP(any(), any(), any())(any(), any())
    }
  }
}