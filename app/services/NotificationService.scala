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

import common.constants.ETMPStatusCodes
import common.exceptions.DBExceptions.MissingRegDocument
import enums.PAYEStatus
import models.EmpRefNotification
import play.api.Logger
import repositories.{RegistrationMongo, RegistrationMongoRepository}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotificationService @Inject()(injRegistrationMongoRepository: RegistrationMongo) extends NotificationSrv{
  val registrationRepo: RegistrationMongoRepository = injRegistrationMongoRepository.store
}

trait NotificationSrv extends ETMPStatusCodes {

  val registrationRepo: RegistrationMongoRepository

  private[services] def getNewApplicationStatus(status: String): PAYEStatus.Value = {
    status match {
      case APPROVED =>
        Logger.info(s"[NotificationService] - [logNotificationStatus]: Notification has status $APPROVED - Application approved")
        PAYEStatus.acknowledged
      case APPROVED_WITH_CONDITIONS =>
        Logger.info(s"[NotificationService] - [logNotificationStatus]: Notification has status $APPROVED_WITH_CONDITIONS - Application approved but with conditions")
        PAYEStatus.acknowledged
      case REJECTED =>
        Logger.info(s"[NotificationService] - [logNotificationStatus]: Notification has status $REJECTED - Application has been rejected")
        PAYEStatus.rejected
      case REJECTED_UNDER_REVIEW_APPEAL =>
        Logger.info(s"[NotificationService] - [logNotificationStatus]: Notification has status $REJECTED_UNDER_REVIEW_APPEAL - Application has been rejected even after a review or appeal")
        PAYEStatus.rejected
      case REVOKED =>
        Logger.info(s"[NotificationService] - [logNotificationStatus]: Notification has status $REVOKED - Application has been revoked")
        PAYEStatus.rejected
      case REVOKED_UNDER_REVIEW_APPEAL =>
        Logger.info(s"[NotificationService] - [logNotificationStatus]: Notification has status $REVOKED_UNDER_REVIEW_APPEAL - Application has been revoked even after a review or appeal")
        PAYEStatus.rejected
      case DEREGISTERED =>
        Logger.info(s"[NotificationService] - [logNotificationStatus]: Notification has status $DEREGISTERED - Application has been de registered")
        PAYEStatus.rejected
    }
  }

  def processNotification(ackRef: String, notification: EmpRefNotification)(implicit ec: ExecutionContext): Future[EmpRefNotification] = {
    for {
      empRefNotification <- registrationRepo.updateRegistrationEmpRef(ackRef, getNewApplicationStatus(notification.status), notification)
      oReg <- registrationRepo.retrieveRegistrationByAckRef(ackRef)
      reg = oReg.getOrElse{throw new MissingRegDocument(s"No registration ID found for ack ref $ackRef when processing ETMP notification")}
      _ <- registrationRepo.updateRegistrationStatus(reg.registrationID, getNewApplicationStatus(notification.status))
    } yield empRefNotification
  }
}
