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

package auth

import play.api.mvc.Result
import play.api.mvc.Results._
import play.api.Logger
import connectors.{AuthConnector, Authority}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait AuthorisationResult {}
case object NotLoggedInOrAuthorised extends AuthorisationResult
final case class NotAuthorised(authContext: Authority) extends AuthorisationResult
final case class Authorised(authContext: Authority) extends AuthorisationResult
final case class AuthResourceNotFound(authContext: Authority) extends AuthorisationResult

trait Authorisation[I] {

  val auth: AuthConnector
  val resourceConn : AuthorisationResource[I]

  def authorised(id:I)(f: => AuthorisationResult => Future[Result])(implicit hc: HeaderCarrier) = {
    for {
      authority <- auth.getCurrentAuthority()
      resource <- resourceConn.getInternalId(id)
      result <- f(mapToAuthResult(authority, resource))
    } yield {
      Logger.debug(s"Got authority = $authority")
      result
    }
  }

  private def mapToAuthResult(authContext: Option[Authority], resource: Option[(I,String)] ) : AuthorisationResult = {
    authContext match {
      case None => NotLoggedInOrAuthorised
      case Some(context) => {
        resource match {
          case None => AuthResourceNotFound(context)
          case Some((_, context.ids.internalId)) => Authorised (context)
          case Some((_, _)) => NotAuthorised (context)
        }
      }
    }
  }
}
