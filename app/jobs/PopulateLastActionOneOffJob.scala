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

import javax.inject.{Inject, Singleton}

import org.joda.time.Duration
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}
import uk.gov.hmrc.play.scheduling.ExclusiveScheduledJob
import utils.PAYEFeatureSwitches
import play.api.Logger
import repositories.RegistrationMongo

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PopulateLastActionOneOffJobImpl @Inject()(mRepo: RegistrationMongo) extends PopulateLastActionOneOffJob {
  val name: String = "populate-last-action-one-off-job"
  val mongoRepo = mRepo
  override lazy val lock: LockKeeper = new LockKeeper() {
    override val lockId = s"$name-lock"
    override val forceLockReleaseAfter: Duration = lockTimeout
    private implicit val mongo = new MongoDbConnection {}.db
    override val repo = new LockRepository
  }
}

trait PopulateLastActionOneOffJob extends ExclusiveScheduledJob with JobConfig {

  val lock:LockKeeper
  val mongoRepo : RegistrationMongo

  override def executeInMutex(implicit ec: ExecutionContext): Future[Result] = {
    PAYEFeatureSwitches.populateLastAction.enabled match {
      case true =>
        lock.tryLock{
          Logger.info(s"Triggered $name")
          mongoRepo.store.populateLastAction.map { s =>
            val (successes, failures) = s
            val message = s"updated $successes documents successfully with $failures failures"
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
      case false => Future.successful(Result(s"Populate last action Feature is turned off"))
    }
  }
}
