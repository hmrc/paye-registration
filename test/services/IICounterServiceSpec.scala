
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
  }



}
