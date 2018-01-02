/*
 * Copyright 2018 HM Revenue & Customs
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
import models.validation.APIValidation
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.http.Status.{ACCEPTED, OK}
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.Future
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import scala.util.control.NoStackTrace
import uk.gov.hmrc.http.{CorePost, HeaderCarrier, HttpResponse}

class IncorporationInformationResponseException(msg: String) extends NoStackTrace {
  override def getMessage: String = msg
}

@Singleton
class IncorporationInformationConnector extends IncorporationInformationConnect with ServicesConfig {
  val http = WSHttp
  lazy val incorporationInformationUri: String = baseUrl("incorporation-information")
  lazy val payeRegUri: String = baseUrl("paye-registration")
}

trait IncorporationInformationConnect {
  val http: CorePost
  val incorporationInformationUri: String
  val payeRegUri: String

  private[connectors] def constructIncorporationInfoUri(transactionId: String, regime: String, subscriber: String): String = {
    s"/incorporation-information/subscribe/$transactionId/regime/$regime/subscriber/$subscriber"
  }

  def getIncorporationUpdate(transactionId: String, regime: String, subscriber: String, regId: String)(implicit hc: HeaderCarrier): Future[Option[IncorpStatusUpdate]] = {
    val postJson = Json.obj("SCRSIncorpSubscription" -> Json.obj("callbackUrl" -> s"$payeRegUri/paye-registration/incorporation-data"))
    http.POST[JsObject, HttpResponse](s"$incorporationInformationUri${constructIncorporationInfoUri(transactionId, regime, subscriber)}", postJson) map { resp =>
      resp.status match {
        case OK => Some(resp.json.as[IncorpStatusUpdate](IncorpStatusUpdate.reads(APIValidation)))
        case ACCEPTED => None
        case _ =>
          Logger.error(s"[IncorporationInformationConnect] - [getIncorporationUpdate] returned a ${resp.status} response code for regId: $regId and txId: $transactionId")
          throw new IncorporationInformationResponseException(s"Calling II on ${constructIncorporationInfoUri(transactionId, regime, subscriber)} returned a ${resp.status}")
      }
    }
  }
}
