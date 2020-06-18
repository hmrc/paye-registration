/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.ZonedDateTime

import config.AppConfig
import javax.inject.Inject
import jobs._
import org.joda.time.Duration
import play.api.Logger
import repositories.RegistrationMongoRepository
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}

import scala.concurrent.{ExecutionContext, Future}

class RemoveStaleDocsService @Inject()(registrationMongoRepository: RegistrationMongoRepository,
                                       lockRepository: LockRepositoryProvider,
                                       appConfig: AppConfig) extends ScheduledService[Either[(ZonedDateTime, Int), LockResponse]] {

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
        Logger.info(message)
        Logger.info("RemoveStaleDocsService acquired lock and returned results")
        Left(res)
      case None =>
        Logger.info("RemoveStaleDocsService cant acquire lock")
        Right(MongoLocked)
    }.recover {
      case e => Logger.error(s"Error running RemoveStaleDocsService with message: ${e.getMessage}")
        Right(UnlockingFailed)
    }
  }


  def removeStaleDocs(implicit ec: ExecutionContext): Future[(ZonedDateTime, Int)] = {
    registrationMongoRepository.removeStaleDocuments()

  }
}
