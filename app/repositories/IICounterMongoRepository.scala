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

package repositories

import javax.inject.{Inject, Singleton}

import common.exceptions.DBExceptions.UpdateFailed
import models.IICounter
import play.api.libs.json.JsValue
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class IICounterMongo @Inject()(
                                injReactiveMongoComponent: ReactiveMongoComponent) {
  val store = new IICounterMongoRepository(injReactiveMongoComponent.mongoConnector.db)
}

trait IICounterRepository{
  def getNext(regId: String)(implicit ec: ExecutionContext): Future[Int]
}


 class IICounterMongoRepository(mongo: () => DB)
  extends ReactiveRepository[IICounter, BSONObjectID](
    collectionName = "IICounterCollection",
    domainFormat = IICounter.format,
    mongo = mongo) with IICounterRepository {

  def getNext(regId: String)(implicit ec: ExecutionContext): Future[Int] = {
    val selector = BSONDocument("_id" -> regId)
    val modifier = BSONDocument("$inc" -> BSONDocument("count" -> 1))

    collection.findAndUpdate(selector, modifier, fetchNewObject = true, upsert = true)
      .map {
        _.result[JsValue] match {
          case None => throw new UpdateFailed(regId,"IICounter")
          case Some(x) => (x \ "count").as[Int]
        }
      }
  }
}
