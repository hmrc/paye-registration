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

package Mocks

import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.JsObject
import reactivemongo.api.commands.{UpdateWriteResult, DefaultWriteResult}
import reactivemongo.api.indexes.CollectionIndexesManager
import reactivemongo.api.{CollectionProducer, FailoverStrategy, DefaultDB}
import reactivemongo.json.collection.{JSONQueryBuilder, JSONCollection}


import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, ExecutionContext}
import scala.reflect.Manifest

trait MongoMocks extends MockitoSugar {

  def mockMongoDb() = {
    val db = mock[DefaultDB]

    val collection = mock[JSONCollection]

    when(db.collection(any(), any[FailoverStrategy])
    (any[CollectionProducer[JSONCollection]]()))
      .thenReturn(collection)

    val mockIndexManager = mock[CollectionIndexesManager]
    when(mockIndexManager.ensure(any())).thenReturn(Future.successful(true))
    when(collection.indexesManager).thenReturn(mockIndexManager)

    setupAnyUpdateOn(collection)
    setupAnyInsertOn(collection)

    db
  }

  def setupFindFor[T](collection: JSONCollection, returns: Option[T])(implicit manifest: Manifest[T]) = {

    val queryBuilder = mock[JSONQueryBuilder]

    when(
      collection.find(any[JsObject])(any())
    ) thenReturn queryBuilder

    when(
      queryBuilder.one[T](any(), any)
    ) thenReturn Future.successful(returns)

  }

  def mockWriteResult(fails: Boolean = false) = {
    val m = mock[DefaultWriteResult]
    when(m.ok).thenReturn(!fails)
    m
  }

  def mockUpdateWriteResult(fails: Boolean = false) = {
    val m = mock[UpdateWriteResult]
    when(m.ok).thenReturn(!fails)
    m
  }

  def setupAnyInsertOn(collection: JSONCollection, fails: Boolean = false) = {
    val m = mockWriteResult(fails)
    when(collection.insert(any(), any())(any(), any()))
      .thenReturn(Future.successful(m))
  }

  def setupAnyUpdateOn(collection: JSONCollection, fails: Boolean = false) = {
    val m = mockUpdateWriteResult(fails)
    when(
      collection.update(any(), any(), any(), anyBoolean, anyBoolean)(any(), any(), any[ExecutionContext])
    ) thenReturn Future.successful(m)
  }

}
