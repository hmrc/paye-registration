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

package auth

import play.api.mvc.Result
import play.api.Logger
import uk.gov.hmrc.auth.core.{AuthorisationException, AuthorisedFunctions}
import uk.gov.hmrc.auth.core.retrieve.Retrievals.internalId
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

sealed trait AuthorisationResult
case object NotLoggedInOrAuthorised extends AuthorisationResult
final case class NotAuthorised(internalId: String) extends AuthorisationResult
final case class Authorised(internalId: String) extends AuthorisationResult
final case class AuthResourceNotFound(internalId: String) extends AuthorisationResult

trait Authorisation extends AuthorisedFunctions {
  val resourceConn : AuthorisationResource

  def isAuthenticated(f: String => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {
    authorised().retrieve(internalId) { id =>
      id.fold {
        Logger.warn("[Authorisation] - [isAuthenticated] : No internalId present; FORBIDDEN")
        throw new Exception("Missing internalId for the logged in user")
      }(f)
    }
  }

  def isAuthorised(regId: String)(f: => AuthorisationResult => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {
    authorised().retrieve(internalId) { id =>
      resourceConn.getInternalId(regId) flatMap { resource =>
        f(mapToAuthResult(id, resource))
      }
    } recoverWith {
      case ar: AuthorisationException =>
        Logger.warn(s"[Authorisation] - [isAuthorised]: An error occurred, err: ${ar.getMessage}")
        f(NotLoggedInOrAuthorised)
      case e =>
        throw e
    }
  }

  private def mapToAuthResult(internalId: Option[String], resource: Option[String] ) : AuthorisationResult = {
    internalId match {
      case None =>
        Logger.warn("[Authorisation] - [mapToAuthResult]: No internalId was found")
        NotLoggedInOrAuthorised
      case Some(id) => {
        resource match {
          case None =>
            Logger.info("[Authorisation] - [mapToAuthResult]: No auth resource was found for the current user")
            AuthResourceNotFound(id)
          case Some(resourceId) if resourceId == id =>
            Authorised(id)
          case _ =>
            Logger.warn("[Authorisation] - [mapToAuthResult]: The current user is not authorised to access this resource")
            NotAuthorised(id)
        }
      }
    }
  }
}
