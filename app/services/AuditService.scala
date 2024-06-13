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

package services

import audit._
import common.exceptions.DBExceptions.MissingRegDocument
import enums.{AddressTypes, IncorporationStatus}
import models.submission.{DESCompletionCapacity, TopUpDESSubmission}
import play.api.libs.json.{JsObject, Json, Writes}
import repositories.RegistrationMongoRepository
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuditService @Inject()(registrationRepository: RegistrationMongoRepository, val authConnector: AuthConnector, val auditConnector: AuditConnector)
                            (implicit ec: ExecutionContext) extends AuthorisedFunctions {

  private[services] def now() = Instant.now()
  private[services] def eventId() = UUID.randomUUID().toString

  def sendEvent[T](auditType: String, detail: T, transactionName: Option[String] = None)
                  (implicit hc: HeaderCarrier, fmt: Writes[T]): Future[AuditResult] = {

    val event = ExtendedDataEvent(
      auditSource = auditConnector.auditingConfig.auditSource,
      auditType   = auditType,
      eventId     = eventId(),
      tags        = hc.toAuditTags(
        transactionName = transactionName.getOrElse(auditType),
        path = hc.otherHeaders.collectFirst { case (RegistrationAuditEventConstants.PATH, value) => value }.getOrElse("-")
      ),
      detail      = Json.toJson(detail),
      generatedAt = now()
    )

    auditConnector.sendExtendedEvent(event)
  }

  def auditCompletionCapacity(regID: String, previousCC: String, newCC: String)(implicit hc: HeaderCarrier): Future[AuditResult] = {
    authorised().retrieve(Retrievals.externalId and Retrievals.credentials) {
      case Some(id) ~ Some(credentials) =>
        sendEvent(
          auditType = "completionCapacityAmendment",
          detail = AmendCompletionCapacityEventDetail(
            id,
            credentials.providerId,
            regID,
            DESCompletionCapacity.buildDESCompletionCapacity(Some(previousCC)),
            DESCompletionCapacity.buildDESCompletionCapacity(Some(newCC))
          )
        )
      case _ => throw new Exception("[Audit Completion Capacity] failed")
    }
  }

  private[services] def fetchAddressAuditRefs(regId: String): Future[Map[AddressTypes.Value, String]] = {
    registrationRepository.retrieveRegistration(regId) map { oReg =>
      oReg.map { reg =>
        List(
          reg.companyDetails.flatMap(_.roAddress.auditRef).map(AddressTypes.roAdddress -> _),
          reg.companyDetails.flatMap(_.ppobAddress.auditRef).map(AddressTypes.ppobAdddress -> _),
          reg.payeContact.flatMap(_.correspondenceAddress.auditRef).map(AddressTypes.correspondenceAdddress -> _)
        ).flatten.toMap
      }.getOrElse {
        throw new MissingRegDocument(s"No registration found when fetching address audit refs for regID $regId")
      }
    }
  }

  def auditDESSubmission(regId: String, desSubmissionState: String, jsSubmission: JsObject, ctutr: Option[String])(implicit hc: HeaderCarrier): Future[AuditResult] = {
    authorised().retrieve(Retrievals.externalId and Retrievals.credentials) {
      case Some(id) ~ Some(credentials) =>
        for {
          auditRefs <- fetchAddressAuditRefs(regId)
          auditRes <- sendEvent(
            auditType = "payeRegistrationSubmission",
            detail = DesSubmissionAuditEventDetail(id, credentials.providerId, regId, ctutr, desSubmissionState, jsSubmission, auditRefs)
          )
        } yield auditRes
      case _ => throw new Exception("[Audit DES Submission] failed")
    }
  }

  def auditDESTopUp(regId: String, topUpDESSubmission: TopUpDESSubmission)(implicit hc: HeaderCarrier) = {
    topUpDESSubmission.status match {
      case IncorporationStatus.accepted =>
        sendEvent(
          auditType = "payeRegistrationAdditionalData",
          detail = DesTopUpAuditEventDetail(regId, Json.toJson[TopUpDESSubmission](topUpDESSubmission)(TopUpDESSubmission.auditWrites).as[JsObject])
        )
      case IncorporationStatus.rejected =>
        sendEvent(
          "incorporationFailure",
          IncorporationFailureAuditEventDetail(regId, topUpDESSubmission.acknowledgementReference)
        )
    }
  }
}
