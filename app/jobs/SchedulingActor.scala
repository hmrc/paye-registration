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

package jobs

import org.apache.pekko.actor.{Actor, Props}
import jobs.SchedulingActor.ScheduledMessage
import utils.Logging
import services._

import java.time.ZonedDateTime
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class SchedulingActor (implicit ec: ExecutionContext) extends Actor with Logging {

  override def receive: Receive = {
    case message: ScheduledMessage[_] =>
      logger.info(s"Received ${message.getClass.getSimpleName}")
      message.service.invoke
  }
}

object SchedulingActor {

  sealed trait ScheduledMessage[A] {
    val service: ScheduledService[A]
  }

  case class RemoveStaleDocumentsJob(service: RemoveStaleDocsService) extends ScheduledMessage[Either[(ZonedDateTime, Int), LockResponse]]

  case class MetricsJob(service: MetricsService) extends ScheduledMessage[Either[Map[String, Int], LockResponse]]

  def props: Props = Props[SchedulingActor]
}