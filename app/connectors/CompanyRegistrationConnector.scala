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

package connectors

import javax.inject.{Inject, Singleton}

import play.api.Logger
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

@Singleton
class CompanyRegistrationConnector @Inject()(val http: HttpClient, val servicesConfig: ServicesConfig) extends CompanyRegistrationConnect {
  val compRegUrl = servicesConfig.baseUrl("company-registration")

}

trait CompanyRegistrationConnect {
  val compRegUrl: String
  val http: CoreGet

  def fetchCompanyRegistrationDocument(regId: String, txId: Option[String])(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    http.GET[HttpResponse](s"$compRegUrl/company-registration/corporation-tax-registration/$regId/corporation-tax-registration") recover {
      case e: NotFoundException =>
        Logger.error(s"[CompanyRegistrationConnector] - [fetchCompanyRegistrationDocument] : Received a NotFound status code when expecting reg document from Company-Registration for regId: $regId and txId: $txId")
        throw e
      case e: ForbiddenException =>
        Logger.error(s"[CompanyRegistrationConnector] - [fetchCompanyRegistrationDocument] : Received a Forbidden status code when expecting reg document from Company-Registration for regId: $regId and txId: $txId")
        throw e
      case e: Exception =>
        Logger.error(s"[CompanyRegistrationConnector] - [fetchCompanyRegistrationDocument] : Received error when expecting reg document from Company-Registration for regId: $regId and txId: $txId - Error ${e.getMessage}")
        throw e
    }
  }
}
