/*
 * Copyright 2018 HM Revenue & Customs
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
import org.mockito.ArgumentMatchers
import repositories.IICounterMongoRepository
import org.mockito.Mockito._
import play.api.Configuration

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

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

  "calling updateIncorpCount" should {
    "return false if count is less than the maxCount of 2" in new Setup{

      when(mockCounterRepository.getNext(ArgumentMatchers.eq(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(1))

        val result = await(service.updateIncorpCount(regId))
        result shouldBe false
    }


    "return true if count is more than maxCount" in new Setup{
      when(mockCounterRepository.getNext(ArgumentMatchers.eq(regId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(4))

      val result = await(service.updateIncorpCount(regId))
      result shouldBe true
    }
  }



}
