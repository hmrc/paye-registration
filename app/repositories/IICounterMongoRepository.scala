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

package repositories

import javax.inject.{Inject, Singleton}

import models.IICounter
import play.api.{Configuration, Logger}
import reactivemongo.api.{DB, ReadPreference}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Success

@Singleton
class IICounterMongo @Inject()(
                                injReactiveMongoComponent: ReactiveMongoComponent) {
  val store = IICounterMongoRepository(injReactiveMongoComponent.mongoConnector.db)
}

trait IICounterRepository{
  def addCompanyToCounterDB(regId: String): Future[Boolean]
  def removeCompanyFromCounterDB(regID: String): Future[Boolean]
  def getCompanyFromCounterDB(regID: String): Future[Option[IICounter]]
  def incrementCount(regID: String): Future[Boolean]


}


case class IICounterMongoRepository(mongo: () => DB)
  extends ReactiveRepository[IICounter, BSONObjectID](
    collectionName = "II-counter-collection",
    domainFormat = IICounter.format,
    mongo = mongo) with IICounterRepository {

  override def addCompanyToCounterDB(regId: String): Future[Boolean] = {
    val iiCounter = IICounter(regID = regId, 0)
    collection.insert(iiCounter).map {
      case _ => true
    } recover {
      case _ => false
    }
  }

  override def removeCompanyFromCounterDB(regID: String): Future[Boolean] = {
    val selector = BSONDocument("_id" -> regID)

    collection.findAndRemove(selector).map(_.result[IICounter]).map {
      case None => false
      case _ => true
    }
  }

  override def incrementCount(regID: String): Future[Boolean] = {
    val selector = BSONDocument("_id" -> regID)
    val modifier = BSONDocument("$inc" -> BSONDocument("counter" -> 1))

    collection.findAndUpdate(selector, modifier,fetchNewObject = true)
      .map(_.result[IICounter]) map {
        case None => false
        case _ => true
      }
  }

  override def getCompanyFromCounterDB(regID: String): Future[Option[IICounter]] = {
    val selector = BSONDocument("_id" -> regID)

    collection.find(selector)
      .cursor[IICounter](ReadPreference.primary)
      .collect[List](1)
      .map{
        _.headOption
      }
  }
}