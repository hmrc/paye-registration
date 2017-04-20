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
import models.incorporation.IncorpStatusUpdate
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.http.Status.{ACCEPTED, OK}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.http.ws.WSPost

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NoStackTrace

class IncorporationInformationResponseException(msg: String) extends NoStackTrace {
  override def getMessage: String = msg
}

@Singleton
class IncorporationInformationConnector @Inject()() extends IncorporationInformationConnect with ServicesConfig {
  val http = WSHttp
  val incorporationInformationUri: String = baseUrl("incorporation-information")
}

trait IncorporationInformationConnect {
  val http: WSPost
  val incorporationInformationUri: String

  private[connectors] def constructIncorporationInfoUri(transactionId: String, regime: String, subscriber: String): String = {
    s"/incorporation-information/subscribe/$transactionId/regime/$regime/subscriber/$subscriber"
  }

  def checkStatus(transactionId: String, regime: String, subscriber: String, callbackUrl: String)(implicit hc: HeaderCarrier): Future[Option[IncorpStatusUpdate]] = {
    val postJson = Json.obj("SCRSIncorpSubscription" -> Json.obj("callbackUrl" -> callbackUrl))
    http.POST[JsObject, HttpResponse](s"$incorporationInformationUri${constructIncorporationInfoUri(transactionId, regime, subscriber)}", postJson) map { resp =>
      resp.status match {
        case OK => Some(resp.json.as[IncorpStatusUpdate])
        case ACCEPTED => None
        case _ => throw new IncorporationInformationResponseException(s"Calling II on ${constructIncorporationInfoUri(transactionId, regime, subscriber)} returned a ${resp.status}")
      }
    }
  }
}
