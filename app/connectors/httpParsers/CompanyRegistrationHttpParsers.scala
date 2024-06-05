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

import connectors.BaseConnector
import play.api.http.Status.{FORBIDDEN, NOT_FOUND, OK}
import uk.gov.hmrc.http.{ForbiddenException, HttpReads, HttpResponse, NotFoundException}

import scala.util.Try

trait CompanyRegistrationHttpParsers extends BaseHttpReads { _: BaseConnector =>

  def ctUtrHttpReads(regId: String, txId: Option[String]): HttpReads[Option[String]] = (_: String, url: String, response: HttpResponse) =>
    response.status match {
      case OK =>
        Try((response.json \ "acknowledgementReferences" \ "ctUtr").as[String]).toOption
      case NOT_FOUND =>
        val msg = "[ctUtrHttpReads] Received a NotFound status code when expecting reg document from Company-Registration" + logContext(Some(regId), txId)
        logger.error(msg)
        throw new NotFoundException(msg)
      case FORBIDDEN =>
        val msg = "[ctUtrHttpReads] Received a Forbidden status code when expecting reg document from Company-Registration" + logContext(Some(regId), txId)
        logger.error(msg)
        throw new ForbiddenException(msg)
      case status =>
        unexpectedStatusHandling()("ctUtrHttpReads", url, status, Some(regId), txId)
    }
}

object CompanyRegistrationHttpParsers extends CompanyRegistrationHttpParsers with BaseConnector
