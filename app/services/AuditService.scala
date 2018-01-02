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

package services

import javax.inject.{Inject, Singleton}

import common.exceptions.DBExceptions.MissingRegDocument
import audit._
import config.MicroserviceAuditConnector
import connectors.{AuthConnect, AuthConnector}
import enums.{AddressTypes, IncorporationStatus}
import models.submission.{DESCompletionCapacity, TopUpDESSubmission}
import play.api.libs.json.{JsObject, Json}
import repositories.{RegistrationMongo, RegistrationRepository}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class AuditService @Inject()(injRegistrationMongoRepository: RegistrationMongo,
                             injAuthConnector: AuthConnector) extends AuditSrv {
  val registrationRepository = injRegistrationMongoRepository.store
  val authConnector = injAuthConnector
  val auditConnector = MicroserviceAuditConnector
}

trait AuditSrv {
  val registrationRepository: RegistrationRepository
  val authConnector : AuthConnect
  val auditConnector : AuditConnector

  def auditCompletionCapacity(regID: String, previousCC: String, newCC: String)(implicit hc: HeaderCarrier): Future[AuditResult] = {
    for {
      authority <- authConnector.getCurrentAuthority
      userDetails <- authConnector.getUserDetails
      authProviderId = userDetails.get.authProviderId
      event = new AmendCompletionCapacityEvent(regID,
                                               AmendCompletionCapacityEventDetail(authority.get.ids.externalId,
                                                                                  authProviderId,
                                                                                  regID,
                                                                                  DESCompletionCapacity.buildDESCompletionCapacity(Some(previousCC)),
                                                                                  DESCompletionCapacity.buildDESCompletionCapacity(Some(newCC))))
      auditRes <- auditConnector.sendExtendedEvent(event)
    } yield auditRes
  }

  private[services] def fetchAddressAuditRefs(regId: String)(implicit ec: ExecutionContext): Future[Map[AddressTypes.Value, String]] = {
    registrationRepository.retrieveRegistration(regId) map { oReg =>
      oReg.map { reg =>
        List(
          reg.companyDetails.flatMap(_.roAddress.auditRef).map( AddressTypes.roAdddress -> _ ),
          reg.companyDetails.flatMap(_.ppobAddress.auditRef).map( AddressTypes.ppobAdddress -> _ ),
          reg.payeContact.flatMap(_.correspondenceAddress.auditRef).map( AddressTypes.correspondenceAdddress -> _ )
        ).flatten.toMap
      }.getOrElse{ throw new MissingRegDocument(s"No registration found when fetching address audit refs for regID $regId") }
    }
  }

  def auditDESSubmission(regId: String, desSubmissionState: String, jsSubmission: JsObject, ctutr: Option[String])(implicit hc: HeaderCarrier): Future[AuditResult] = {
    for {
      authority <- authConnector.getCurrentAuthority
      userDetails <- authConnector.getUserDetails
      authProviderId = userDetails.get.authProviderId
      auditRefs <- fetchAddressAuditRefs(regId)
      event = new DesSubmissionEvent(DesSubmissionAuditEventDetail(authority.get.ids.externalId, authProviderId, regId, ctutr, desSubmissionState, jsSubmission, auditRefs))
      auditRes <- auditConnector.sendExtendedEvent(event)
    } yield auditRes
  }

  def auditDESTopUp(regId: String, topUpDESSubmission: TopUpDESSubmission)(implicit hc: HeaderCarrier) = {
    val event: RegistrationAuditEvent = topUpDESSubmission.status match {
      case IncorporationStatus.accepted => new DesTopUpEvent(DesTopUpAuditEventDetail(regId, Json.toJson[TopUpDESSubmission](topUpDESSubmission)(TopUpDESSubmission.auditWrites).as[JsObject]))
      case IncorporationStatus.rejected => new IncorporationFailureEvent(IncorporationFailureAuditEventDetail(regId, topUpDESSubmission.acknowledgementReference))
    }
    auditConnector.sendExtendedEvent(event)
  }
}
