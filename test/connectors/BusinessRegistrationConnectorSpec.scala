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

package connectors

import config.AppConfig
import helpers.PAYERegSpec
import mocks.HTTPMock
import models.external.BusinessProfile
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.test.Helpers._
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BusinessRegistrationConnectorSpec extends PAYERegSpec with HTTPMock {

  trait Setup {

    object MockAppConfig extends AppConfig(mock[ServicesConfig]) {
      override lazy val businessRegUrl: String = "testBusinessRegUrl"
    }

    object Connector extends BusinessRegistrationConnector(mockHttp, MockAppConfig)

  }

  lazy val validBusinessRegistrationResponse: BusinessProfile = BusinessProfile(
    "12345",
    Some("director"),
    "ENG"
  )

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "retrieveCurrentProfile" should {
    "return a a CurrentProfile response if one is found in business registration micro-service" in new Setup {
      mockHttpGet[BusinessProfile]("testUrl", validBusinessRegistrationResponse)

      await(Connector.retrieveCurrentProfile("12345")) shouldBe validBusinessRegistrationResponse
    }

    "return a Not Found response when a CurrentProfile record can not be found" in new Setup {
      when(mockHttp.GET[BusinessProfile](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("Bad request")))

      intercept[NotFoundException](await(Connector.retrieveCurrentProfile("12345")))
    }

    "return a Forbidden response when a CurrentProfile record can not be accessed by the user" in new Setup {
      when(mockHttp.GET[BusinessProfile](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new ForbiddenException("Forbidden")))

      intercept[ForbiddenException](await(Connector.retrieveCurrentProfile("12345")))
    }

    "return an Exception response when an unspecified error has occurred" in new Setup {
      when(mockHttp.GET[BusinessProfile](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new RuntimeException("Runtime Exception")))

      intercept[RuntimeException](await(Connector.retrieveCurrentProfile("12345")))
    }
  }
}
