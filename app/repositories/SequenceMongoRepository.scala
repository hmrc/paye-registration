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

import models.Sequence
import play.api.Logger
import play.api.libs.json.JsValue
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

@Singleton
class SequenceMongoRepository @Inject()(mongo: ReactiveMongoComponent) extends ReactiveRepository[Sequence, BSONObjectID](
  "sequence",
  mongo.mongoConnector.db,
  Sequence.formats,
  ReactiveMongoFormats.objectIdFormats
) with ReactiveMongoFormats {

  def getNext(sequenceID: String)(implicit ec: ExecutionContext): Future[Int] = {
    val selector = BSONDocument("_id" -> sequenceID)
    val modifier = BSONDocument("$inc" -> BSONDocument("seq" -> 1))

    collection.findAndUpdate(selector, modifier, fetchNewObject = true, upsert = true) map {
      _.result[JsValue] match {
        // $COVERAGE-OFF$
        case None => {
          Logger.error("[SequenceRepository] - [getNext] returned a None when Upserting")
          class InvalidSequence extends NoStackTrace
          throw new InvalidSequence
        }
        // $COVERAGE-ON$
        case Some(res) => (res \ "seq").as[Int]
      }
    }
  }
}
