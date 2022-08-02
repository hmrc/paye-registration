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

import auth._
import play.api.Logging
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

package object controllers extends Logging {

  implicit class HandleAuthResult(authResult: AuthorisationResult)(implicit hc: HeaderCarrier) {
    def ifAuthorised(regID: String, controller: String, method: String)(f: => Future[Result]): Future[Result] = authResult match {
      case Authorised(_) =>
        f
      case NotLoggedInOrAuthorised =>
        logger.info(s"[$controller] [$method] User not logged in")
        Future.successful(Forbidden)
      case NotAuthorised(_) =>
        logger.info(s"[$controller] [$method] User logged in but not authorised for resource $regID")
        Future.successful(Forbidden)
      case AuthResourceNotFound(_) =>
        logger.info(s"[$controller] [$method] User logged in but no resource found for regId $regID")
        Future.successful(NotFound)
    }
  }

}
