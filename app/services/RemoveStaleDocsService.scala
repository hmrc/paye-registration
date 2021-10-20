/*
 * Copyright 2021 HM Revenue & Customs
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
import org.joda.time.Duration
import play.api.Logging
import repositories.RegistrationMongoRepository
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}

import java.time.ZonedDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RemoveStaleDocsService @Inject()(registrationMongoRepository: RegistrationMongoRepository,
                                       lockRepository: LockRepositoryProvider,
                                       appConfig: AppConfig) extends ScheduledService[Either[(ZonedDateTime, Int), LockResponse]] with Logging {

  lazy val lock: LockKeeper = new LockKeeper() {
    override val lockId: String = "remove-stale-documents-job"
    override val forceLockReleaseAfter: Duration = Duration.standardSeconds(appConfig.removeStaleDocumentsJobLockoutTimeout)
    override lazy val repo: LockRepository = lockRepository.repo
  }

  def invoke(implicit ec: ExecutionContext): Future[Either[(ZonedDateTime, Int), LockResponse]] = {
    lock.tryLock(removeStaleDocs).map {
      case Some(res) =>
        val (dt, numberRemoved) = res
        val message = numberRemoved match {
          case 0 => s"No documents removed as there were no documents older than $dt in the database"
          case _ => s"Successfully removed $numberRemoved documents that were last updated before $dt"
        }
        logger.info(message)
        logger.info("RemoveStaleDocsService acquired lock and returned results")
        Left(res)
      case None =>
        logger.info("RemoveStaleDocsService cant acquire lock")
        Right(MongoLocked)
    }.recover {
      case e => logger.error(s"Error running RemoveStaleDocsService with message: ${e.getMessage}")
        Right(UnlockingFailed)
    }
  }


  def removeStaleDocs(implicit ec: ExecutionContext): Future[(ZonedDateTime, Int)] = {
    registrationMongoRepository.removeStaleDocuments()

  }
}
