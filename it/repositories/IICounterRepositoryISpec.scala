

package repositories

import common.exceptions.DBExceptions.{DeleteFailed, InsertFailed}
import itutil.MongoBaseSpec
import models.IICounter
import play.api.Configuration

import scala.concurrent.Await
import scala.concurrent.duration.Duration
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

  "calling addCompanyToCounterDB" should {

    "return true if company has been added" in new Setup {
      val result = await(repository.addCompanyToCounterDB(newCompanyReg))
      result shouldBe true
    }

    "return false if an error occured" in new Setup {
      await(repository.addCompanyToCounterDB(newCompanyReg))
      val result = await(repository.addCompanyToCounterDB(newCompanyReg))
      result shouldBe false
    }
  }

  "calling getCounterFromDB" should {

    "return the object if it exists in the Counter database" in new Setup {
      await(repository.addCompanyToCounterDB(newCompanyReg))
      val result = await(repository.getCompanyFromCounterDB(newCompanyReg))
      result shouldBe Some(newCompanyForCounter)
    }

    "retrun none if it doesnt exist" in new Setup {
      val result = await(repository.getCompanyFromCounterDB(nonExistentReg))
      result shouldBe None
    }
  }

  "calling incrementCount" should{

    "increment count and return true if company exists" in new Setup{
      await(repository.addCompanyToCounterDB(newCompanyReg))
      val result = await(repository.incrementCount(newCompanyReg))
      val newCompany = await(repository.getCompanyFromCounterDB(newCompanyReg))

      result shouldBe true
      newCompany match {
        case None => fail("Expected an IICounter got None")
        case Some(IICounter(id, count)) =>
          id shouldBe newCompanyReg
          count shouldBe 1
      }
    }

    "return a false if the company doesn't exist" in new Setup{
      val result = await(repository.incrementCount(newCompanyReg))
      result shouldBe false
    }
  }

  "calling removeCompanyFromCounterDB" should {

    "return true if deleted" in new Setup {
      await(repository.addCompanyToCounterDB(newCompanyReg))
      val result = await(repository.removeCompanyFromCounterDB(newCompanyReg))
      result shouldBe true
    }

    "return false if an error occurred" in new Setup {
      val result = await(repository.removeCompanyFromCounterDB(newCompanyReg))
      result shouldBe false
    }
  }
}
