/*
 * Copyright 2022 HM Revenue & Customs
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

import com.mongodb.client.model.ReturnDocument
import models.IICounter
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.FindOneAndUpdateOptions
import org.mongodb.scala.model.Updates.inc
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class IICounterMongoRepository @Inject()(mongoComponent: MongoComponent)(implicit ec: ExecutionContext) extends PlayMongoRepository[IICounter](
  mongoComponent = mongoComponent,
  collectionName = "IICounterCollection",
  domainFormat = IICounter.format,
  indexes = Seq()
) {

  def getNext(regId: String)(implicit ec: ExecutionContext): Future[Int] = {
    val selector = equal("_id", regId)
    val modifier = inc("count", 1)

    collection.findOneAndUpdate(
      selector,
      modifier,
      FindOneAndUpdateOptions()
        .upsert(true)
        .returnDocument(ReturnDocument.AFTER)
    ).toFuture().map(_.counter)
  }
}
