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

import javax.inject.{Inject, Singleton}

import config.WSHttp
import models.external.BusinessProfile
import play.api.Logger
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{ForbiddenException, HeaderCarrier, HttpReads, NotFoundException}
import uk.gov.hmrc.play.http.ws.WSHttp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class BusinessRegistrationConnector @Inject()() extends BusinessRegistrationConnect with ServicesConfig {
  val businessRegUrl = baseUrl("business-registration")
  val http : WSHttp = WSHttp
}

trait BusinessRegistrationConnect {

  val businessRegUrl: String
  val http: WSHttp

  def retrieveCurrentProfile(regId: String)(implicit hc: HeaderCarrier, rds: HttpReads[BusinessProfile]): Future[BusinessProfile] = {
    http.GET[BusinessProfile](s"$businessRegUrl/business-registration/business-tax-registration") recover {
      case e: NotFoundException =>
        Logger.error(s"[BusinessRegistrationConnector] [retrieveCurrentProfile] - Received a NotFound status code when expecting current profile from Business-Registration for regId: $regId")
        throw e
      case e: ForbiddenException =>
        Logger.error(s"[BusinessRegistrationConnector] [retrieveCurrentProfile] - Received a Forbidden status code when expecting current profile from Business-Registration for regId: $regId")
        throw e
      case e: Exception =>
        Logger.error(s"[BusinessRegistrationConnector] [retrieveCurrentProfile] - Received error when expecting current profile from Business-Registration for regId: $regId - Error ${e.getMessage}")
        throw e
    }
  }
}
