

package repositories

import itutil.MongoBaseSpec
import models.IICounter

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
