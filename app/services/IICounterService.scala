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

package services

import javax.inject.Inject

import models.IICounter
import play.api.Configuration
import repositories.{IICounterMongo, IICounterMongoRepository}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class IICounterService @Inject()(injIICounterMongo: IICounterMongo,
                                 config: Configuration) extends IICounterSrv{

  lazy val maxIICounterCount: Int = config.getInt("constants.maxIICounterCount").getOrElse(2)
  val counterRepository = injIICounterMongo.store
}

trait IICounterSrv{
  val counterRepository: IICounterMongoRepository
  val maxIICounterCount: Int

  private def getCountFromCounter(counter: Option[IICounter]): Int = counter match {
    case None => maxIICounterCount + 2
    case Some(result) => result.counter
  }

  private def deleteIfMaxExceeded(count: Int, regID: String): Future[Boolean] = {
    if (count > maxIICounterCount) {
      counterRepository.removeCompanyFromCounterDB(regID)
    } else{
      Future.successful(false)
    }
  }

  def updateIncorpCount(regID: String): Future[Int] = for {
    _       <- counterRepository.addCompanyToCounterDB(regID)
    _       <- counterRepository.incrementCount(regID)
    counter <- counterRepository.getCompanyFromCounterDB(regID)
    _       <- deleteIfMaxExceeded(getCountFromCounter(counter),regID)
  } yield getCountFromCounter(counter)
}
