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
import connectors.httpParsers.IncorporationInformationHttpParsers
import models.incorporation.IncorpStatusUpdate
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

class IncorporationInformationResponseException(msg: String) extends Exception with NoStackTrace {
  override def getMessage: String = msg
}

@Singleton
class IncorporationInformationConnector @Inject()(val http: HttpClient, appConfig: AppConfig) extends BaseConnector with IncorporationInformationHttpParsers {

  private[connectors] def constructIncorporationInfoUri(transactionId: String, regime: String, subscriber: String): String =
    s"/incorporation-information/subscribe/$transactionId/regime/$regime/subscriber/$subscriber"

  def getIncorporationDate(transactionId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[LocalDate]] =
    withRecovery()("getIncorporationDate", txId = Some(transactionId)) {
      http.GET[Option[LocalDate]](s"${appConfig.incorporationInformationUri}/incorporation-information/$transactionId/incorporation-update")(
        incorporationDateHttpReads(transactionId), hc, ec
      )
    }

  def getIncorporationUpdate(transactionId: String, regime: String, subscriber: String, regId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[IncorpStatusUpdate]] = {
    val postJson = Json.obj("SCRSIncorpSubscription" -> Json.obj("callbackUrl" -> s"${appConfig.payeRegUri}/paye-registration/incorporation-data"))
    withRecovery()("getIncorporationUpdate", Some(regId), Some(transactionId)) {
      http.POST[JsObject, Option[IncorpStatusUpdate]](s"${appConfig.incorporationInformationUri}${constructIncorporationInfoUri(transactionId, regime, subscriber)}", postJson)(
        implicitly, incorpStatusUpdateHttpReads(regId, transactionId), hc, ec
      )
    }
  }
}
