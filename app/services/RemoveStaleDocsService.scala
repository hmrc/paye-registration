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
import javax.inject.Inject

import jobs._
import org.joda.time.Duration
import play.api.Logger
import repositories.RegistrationMongo
import uk.gov.hmrc.lock.LockKeeper
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

class RemoveStaleDocsServiceImpl @Inject()(val mRepo: RegistrationMongo,
                                           val lockRepository: LockRepositoryProvider,
                                           val servicesConfig: ServicesConfig) extends RemoveStaleDocsService{

  lazy val lockoutTimeout = servicesConfig.getInt("schedules.remove-stale-documents-job.lockTimeout")
  lazy val lock: LockKeeper = new LockKeeper() {
    override val lockId = "remove-stale-documents-job"
    override val forceLockReleaseAfter: Duration = Duration.standardSeconds(lockoutTimeout)
    override lazy val repo = lockRepository.repo
  }

}


trait RemoveStaleDocsService extends ScheduledService[Either[(ZonedDateTime, Int), LockResponse]] {
  val lock: LockKeeper
  val mRepo: RegistrationMongo

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
    mRepo.store.removeStaleDocuments()

  }
}
