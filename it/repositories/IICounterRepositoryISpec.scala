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

import itutil.MongoBaseSpec
import models.IICounter
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global


class IICounterRepositoryISpec extends MongoBaseSpec{

  private val newCompanyReg = "123-456-1123"
  private val newCompanyForCounter = IICounter(newCompanyReg,0)
  private val incrementedCompanyCounter = IICounter(newCompanyReg,1)
  private val maxCompanyCounter = IICounter(newCompanyReg,2)
  private val nonExistentReg = "ThisShouldntExist"

  private val interrogateCompany = "ABC-123-WOW7"

  class Setup {
    val mongo = new IICounterMongo(reactiveMongoComponent)
    val repository = mongo.store
    await(repository.drop)
  }

  "calling getNext" should{
    "increment and return amount of times II called" in new Setup{
      for(i <- 1 to 3) {
        val result = await(repository.getNext(newCompanyReg))
        result shouldBe i
      }
    }
  }
}
