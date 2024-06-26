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

package auth

import utils.Logging
import play.api.mvc.Result
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.{AuthorisationException, AuthorisedFunctions}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

sealed trait AuthorisationResult
case object NotLoggedInOrAuthorised extends AuthorisationResult
final case class NotAuthorised(internalId: String) extends AuthorisationResult
final case class Authorised(internalId: String) extends AuthorisationResult
final case class AuthResourceNotFound(internalId: String) extends AuthorisationResult

trait Authorisation extends AuthorisedFunctions with Logging {
  val resourceConn : AuthorisationResource
  val internalId: Retrieval[Option[String]] = Retrievals.internalId


  def isAuthenticated(f: String => Future[Result])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] = {
    authorised().retrieve(internalId) { id =>
      id.fold {
        logger.warn("[isAuthenticated] No internalId present; FORBIDDEN")
        throw new Exception("Missing internalId for the logged in user")
      }(f)
    }
  }

  def isAuthorised(regId: String)(f: => AuthorisationResult => Future[Result])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] = {
    authorised().retrieve(internalId) { id =>
      resourceConn.getInternalId(regId) flatMap { resource =>
        f(mapToAuthResult(id, resource))
      }
    } recoverWith {
      case ar: AuthorisationException =>
        logger.warn(s"[isAuthorised] An error occurred, err: ${ar.getMessage}")
        f(NotLoggedInOrAuthorised)
      case e =>
        throw e
    }
  }

  private def mapToAuthResult(internalId: Option[String], resource: Option[String] ) : AuthorisationResult = {
    internalId match {
      case None =>
        logger.warn("[mapToAuthResult] No internalId was found")
        NotLoggedInOrAuthorised
      case Some(id) => {
        resource match {
          case None =>
            logger.warn("[mapToAuthResult] No auth resource was found for the current user")
            AuthResourceNotFound(id)
          case Some(resourceId) if resourceId == id =>
            Authorised(id)
          case _ =>
            logger.warn("[mapToAuthResult] The current user is not authorised to access this resource")
            NotAuthorised(id)
        }
      }
    }
  }
}
