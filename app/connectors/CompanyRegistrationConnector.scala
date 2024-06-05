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
import connectors.httpParsers.CompanyRegistrationHttpParsers
import uk.gov.hmrc.http.{HttpClient, _}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CompanyRegistrationConnector @Inject()(http: HttpClient, appConfig: AppConfig) extends BaseConnector with CompanyRegistrationHttpParsers {

  def fetchCtUtr(regId: String, txId: Option[String])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    withRecovery()("fetchCtUtr", Some(regId), txId) {
      http.GET[Option[String]](s"${appConfig.compRegUrl}/company-registration/corporation-tax-registration/$regId/corporation-tax-registration")(
        ctUtrHttpReads(regId, txId), hc, ec
      )
    }
}
