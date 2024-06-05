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

package connectors.httpParsers

import connectors.{BaseConnector, IncorporationInformationResponseException}
import models.incorporation.IncorpStatusUpdate
import models.validation.APIValidation
import play.api.http.Status.{ACCEPTED, NO_CONTENT, OK}
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

import java.time.LocalDate

trait IncorporationInformationHttpParsers extends BaseHttpReads { _: BaseConnector =>

  override def unexpectedStatusException(url: String, status: Int, regId: Option[String], txId: Option[String]): Exception =
    new IncorporationInformationResponseException(s"Calling II on $url returned a $status" + logContext(regId, txId))

  def incorporationDateHttpReads(txId: String): HttpReads[Option[LocalDate]] = (_: String, url: String, response: HttpResponse) =>
    response.status match {
      case OK => (response.json \ "incorporationDate").asOpt[LocalDate]
      case NO_CONTENT => None
      case status =>
        unexpectedStatusHandling()("incorporationDateHttpReads", url, status, transactionId = Some(txId))
    }

  def incorpStatusUpdateHttpReads(regId: String, txId: String): HttpReads[Option[IncorpStatusUpdate]] = (_: String, url: String, response: HttpResponse) =>
    response.status match {
      case OK =>
        Some(jsonParse[IncorpStatusUpdate](response)("incorpStatusUpdateHttpReads", Some(regId), Some(txId)))
      case ACCEPTED => None
      case status =>
        unexpectedStatusHandling()("incorpStatusUpdateHttpReads", url, status, Some(regId), Some(txId))
    }

}

object IncorporationInformationHttpParsers extends IncorporationInformationHttpParsers with BaseConnector
