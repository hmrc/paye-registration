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

package jobs

import javax.inject.Inject

import org.joda.time.Duration
import play.api.Logger
import play.modules.reactivemongo.MongoDbConnection
import repositories.RegistrationMongo
import uk.gov.hmrc.lock.{LockRepository, LockKeeper}
import uk.gov.hmrc.play.scheduling.ExclusiveScheduledJob
import utils.PAYEFeatureSwitches

import scala.concurrent.{Future, ExecutionContext}

class FindStaleDocumentsJobImpl @Inject()(mRepo: RegistrationMongo) extends FindStaleDocumentsJob {
  val name: String = "find-stale-documents-job"
  val mongoRepo = mRepo
  override lazy val lock: LockKeeper = new LockKeeper() {
    override val lockId = s"$name-lock"
    override val forceLockReleaseAfter: Duration = lockTimeout
    private implicit val mongo = new MongoDbConnection {}.db
    override val repo = new LockRepository
  }
}

trait FindStaleDocumentsJob extends ExclusiveScheduledJob with JobConfig {

  val lock: LockKeeper
  val mongoRepo : RegistrationMongo

  override def executeInMutex(implicit ec: ExecutionContext): Future[Result] = {
    PAYEFeatureSwitches.findStaleDocuments.enabled match {
      case true =>
        lock.tryLock {
          Logger.info(s"Triggered $name")
          mongoRepo.store.findStaleDocuments().map { res =>
            val(dt, numberFound) = res
            val message = numberFound match {
              case 0 => s"No documents found as there were no documents older than $dt in the database"
              case _ => s"Successfully found $numberFound documents that were last updated before $dt"
            }
            Logger.info(message)
            Result(message)
          }
        } map {
          case Some(x) => Logger.info(s"successfully acquired lock for $name - result $x")
            Result(s"$name: ${x.message}")
          case None => Logger.info(s"failed to acquire lock for $name")
            Result(s"$name failed")
        } recover {
          case ex: Exception => Result(s"$name failed. Exception generated: ${ex.getClass.toString}, Reason: ${ex.getMessage}")
        }
      case false => Future.successful(Result(s"Find stale documents feature is turned off"))
    }
  }
}
