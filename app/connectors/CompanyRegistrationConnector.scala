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
import play.api.Logging
import uk.gov.hmrc.http.{HttpClient, _}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class CompanyRegistrationConnector @Inject()(http: HttpClient, appConfig: AppConfig) extends Logging {

  def fetchCompanyRegistrationDocument(regId: String, txId: Option[String])(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    http.GET[HttpResponse](s"${appConfig.compRegUrl}/company-registration/corporation-tax-registration/$regId/corporation-tax-registration") recover {
      case e: NotFoundException =>
        logger.error(s"[CompanyRegistrationConnector] - [fetchCompanyRegistrationDocument] : Received a NotFound status code when expecting reg document from Company-Registration for regId: $regId and txId: $txId")
        throw e
      case e: ForbiddenException =>
        logger.error(s"[CompanyRegistrationConnector] - [fetchCompanyRegistrationDocument] : Received a Forbidden status code when expecting reg document from Company-Registration for regId: $regId and txId: $txId")
        throw e
      case e: Exception =>
        logger.error(s"[CompanyRegistrationConnector] - [fetchCompanyRegistrationDocument] : Received error when expecting reg document from Company-Registration for regId: $regId and txId: $txId - Error ${e.getMessage}")
        throw e
    }
  }
}
