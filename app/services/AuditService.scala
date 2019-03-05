/*
 * Copyright 2019 HM Revenue & Customs
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

import audit._
import common.exceptions.DBExceptions.MissingRegDocument
import enums.{AddressTypes, IncorporationStatus}
import models.submission.{DESCompletionCapacity, TopUpDESSubmission}
import play.api.libs.json.{JsObject, Json}
import repositories.{RegistrationMongo, RegistrationRepository}
import uk.gov.hmrc.auth.core.retrieve.Retrievals.{credentials, externalId}
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuditService @Inject()(injRegistrationMongoRepository: RegistrationMongo, val authConnector: AuthConnector, val auditConnector: AuditConnector) extends AuditSrv {

  val registrationRepository = injRegistrationMongoRepository.store

}

trait AuditSrv extends AuthorisedFunctions {
  val registrationRepository: RegistrationRepository
  val auditConnector : AuditConnector

  def auditCompletionCapacity(regID: String, previousCC: String, newCC: String)(implicit hc: HeaderCarrier): Future[AuditResult] = {
    authorised().retrieve(externalId and credentials) {
      case Some(id) ~ cred =>
        val eventDetails = AmendCompletionCapacityEventDetail(
          id,
          cred.providerId,
          regID,
          DESCompletionCapacity.buildDESCompletionCapacity(Some(previousCC)),
          DESCompletionCapacity.buildDESCompletionCapacity(Some(newCC))
        )
        val event = new AmendCompletionCapacityEvent(regID, eventDetails)
        auditConnector.sendExtendedEvent(event)
    }
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
    authorised().retrieve(externalId and credentials) {
      case Some(id) ~ cred =>
        for {
          auditRefs <- fetchAddressAuditRefs(regId)
          event = new DesSubmissionEvent(DesSubmissionAuditEventDetail(id, cred.providerId, regId, ctutr, desSubmissionState, jsSubmission, auditRefs))
          auditRes <- auditConnector.sendExtendedEvent(event)
        } yield auditRes
    }
  }

  def auditDESTopUp(regId: String, topUpDESSubmission: TopUpDESSubmission)(implicit hc: HeaderCarrier) = {
    val event: RegistrationAuditEvent = topUpDESSubmission.status match {
      case IncorporationStatus.accepted => new DesTopUpEvent(DesTopUpAuditEventDetail(regId, Json.toJson[TopUpDESSubmission](topUpDESSubmission)(TopUpDESSubmission.auditWrites).as[JsObject]))
      case IncorporationStatus.rejected => new IncorporationFailureEvent(IncorporationFailureAuditEventDetail(regId, topUpDESSubmission.acknowledgementReference))
    }
    auditConnector.sendExtendedEvent(event)
  }
}
