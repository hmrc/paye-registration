/*
 * Copyright 2023 HM Revenue & Customs
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

import config.AppConfig
import jobs._
import utils.Logging
import repositories.RegistrationMongoRepository
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}

import java.time.ZonedDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RemoveStaleDocsService @Inject()(registrationMongoRepository: RegistrationMongoRepository,
                                       lockRepository: MongoLockRepository,
                                       appConfig: AppConfig) extends ScheduledService[Either[(ZonedDateTime, Int), LockResponse]] with Logging {

  lazy val lock: LockService = LockService(lockRepository, "remove-stale-documents-job", appConfig.removeStaleDocumentsJobLockoutTimeout.seconds)

  def invoke(implicit ec: ExecutionContext): Future[Either[(ZonedDateTime, Int), LockResponse]] = {
    lock.withLock(removeStaleDocs).map {
      case None => Right(MongoLocked)
      case Some(res) =>
        val (dt, numberRemoved) = res
        val message = numberRemoved match {
          case 0 => s"[invoke] No documents removed as there were no documents older than $dt in the database"
          case _ => s"[invoke] Successfully removed $numberRemoved documents that were last updated before $dt"
        }
        logger.info(message)
        Left(res)
    }.recover {
      case e =>
        logger.error(s"[invoke] Error running RemoveStaleDocsService with message: ${e.getMessage}")
        Right(UnlockingFailed)
    }
  }


  def removeStaleDocs(implicit ec: ExecutionContext): Future[(ZonedDateTime, Int)] = {
    registrationMongoRepository.removeStaleDocuments()

  }
}
