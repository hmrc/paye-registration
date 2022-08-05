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
import models.incorporation.IncorpStatusUpdate
import models.validation.APIValidation
import play.api.Logging
import play.api.http.Status.{ACCEPTED, NO_CONTENT, OK}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

class IncorporationInformationResponseException(msg: String) extends NoStackTrace {
  override def getMessage: String = msg
}

@Singleton
class IncorporationInformationConnector @Inject()(val http: HttpClient, appConfig: AppConfig) extends Logging {

  private[connectors] def constructIncorporationInfoUri(transactionId: String, regime: String, subscriber: String): String = {
    s"/incorporation-information/subscribe/$transactionId/regime/$regime/subscriber/$subscriber"
  }

  def getIncorporationDate(transactionId: String)(implicit hc: HeaderCarrier): Future[Option[LocalDate]] = {
    val url = s"${appConfig.incorporationInformationUri}/incorporation-information/$transactionId/incorporation-update"
    http.GET[HttpResponse](url) map { res =>
      res.status match {
        case OK => (res.json \ "incorporationDate").asOpt[LocalDate]
        case NO_CONTENT => None
        case _ =>
          logger.error(s"[IncorporationInformationConnect] - [getIncorporationDate] returned a ${res.status} response code for txId: $transactionId")
          throw new IncorporationInformationResponseException(s"Calling II on $url returned a ${res.status}")
      }
    } recover {
      case e =>
        logger.error(s"[IncorporationInformationConnector] [getIncorporationDate] has encountered an error using transactionId: $transactionId with message: ${e.getMessage}")
        throw e
    }
  }

  def getIncorporationUpdate(transactionId: String, regime: String, subscriber: String, regId: String)(implicit hc: HeaderCarrier): Future[Option[IncorpStatusUpdate]] = {
    val postJson = Json.obj("SCRSIncorpSubscription" -> Json.obj("callbackUrl" -> s"${appConfig.payeRegUri}/paye-registration/incorporation-data"))
    http.POST[JsObject, HttpResponse](s"${appConfig.incorporationInformationUri}${constructIncorporationInfoUri(transactionId, regime, subscriber)}", postJson) map { resp =>
      resp.status match {
        case OK => Some(resp.json.as[IncorpStatusUpdate](IncorpStatusUpdate.reads(APIValidation)))
        case ACCEPTED => None
        case _ =>
          logger.error(s"[IncorporationInformationConnect] - [getIncorporationUpdate] returned a ${resp.status} response code for regId: $regId and txId: $transactionId")
          throw new IncorporationInformationResponseException(s"Calling II on ${constructIncorporationInfoUri(transactionId, regime, subscriber)} returned a ${resp.status}")
      }
    }
  }
}
