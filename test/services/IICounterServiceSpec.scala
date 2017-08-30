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

import helpers.PAYERegSpec
import models.IICounter
import org.mockito.ArgumentMatchers
import repositories.{IICounterMongo, IICounterMongoRepository}
import org.mockito.Mockito._
import play.api.Configuration

import scala.concurrent.Future

/**
  * Created by eric on 29/08/17.
  */
class IICounterServiceSpec extends PAYERegSpec {

  lazy val mockCounterRepository = mock[IICounterMongoRepository]
  lazy val mockConfig = mock[Configuration]


  class Setup{
    val service = new IICounterSrv {
      override val counterRepository: IICounterMongoRepository = mockCounterRepository
      override val maxIICounterCount: Int = 2
    }
  }

  val regId = "AB12345"
  val newCompany = IICounter(regId,0)
  val incrCompany = IICounter(regId,1)
  val oldCompany = IICounter(regId,3)

  "calling updateIncorpCount" should {
    "increment the count and return it" in new Setup{

      when(mockCounterRepository.addCompanyToCounterDB(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      when(mockCounterRepository.incrementCount(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      when(mockCounterRepository.removeCompanyFromCounterDB(ArgumentMatchers.any()))
        .thenReturn(Future.successful(false))

      when(mockCounterRepository.getCompanyFromCounterDB(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(incrCompany)))

        val result = await(service.updateIncorpCount(regId))
        result shouldBe 1
    }

    "return maxCount + 2 if no object was found" in new Setup{
      when(mockCounterRepository.addCompanyToCounterDB(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      when(mockCounterRepository.incrementCount(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      when(mockCounterRepository.removeCompanyFromCounterDB(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      when(mockCounterRepository.getCompanyFromCounterDB(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      val result = await(service.updateIncorpCount(regId))
      result shouldBe service.maxIICounterCount + 2
    }

    "return 3 if object has been called 2 times prior" in new Setup{
      when(mockCounterRepository.addCompanyToCounterDB(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      when(mockCounterRepository.incrementCount(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      when(mockCounterRepository.removeCompanyFromCounterDB(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      when(mockCounterRepository.getCompanyFromCounterDB(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(oldCompany)))

      val result = await(service.updateIncorpCount(regId))
      result shouldBe service.maxIICounterCount + 1
    }
  }



}
