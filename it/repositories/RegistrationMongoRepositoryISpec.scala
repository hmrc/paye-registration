/*
 * Copyright 2016 HM Revenue & Customs
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

import models._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import reactivemongo.api.commands.WriteResult
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{WithFakeApplication, UnitSpec}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class RegistrationMongoRepositoryISpec
  extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with Eventually with WithFakeApplication {

  private val details: CompanyDetails = CompanyDetails(crn = None, companyName = "tstCcompany", tradingName = Some("tstTradingName"))
  private val reg = PAYERegistration(registrationID = "AC123456", formCreationTimestamp = "timestamp", companyDetails = Some(details))

  // Company Details
  private val regNoCompanyDetails = PAYERegistration(registrationID = "AC123456", formCreationTimestamp = "timestamp", companyDetails = None)
  private val details2: CompanyDetails = CompanyDetails(crn = None, companyName = "tstCcompany2", tradingName = Some("tstTradingName2"))
  private val regUpdatedCompanyDetails = PAYERegistration(registrationID = "AC123456", formCreationTimestamp = "timestamp", companyDetails = Some(details2))

  class Setup {
    val repository = RegistrationMongo.store
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  def setupCollection(repo: RegistrationMongoRepository, registration: PAYERegistration): Future[WriteResult] = {
    repo.insert(registration)
  }

  "Registration repository" should {
    "retrieve a registration object" in new Setup {

      await(setupCollection(repository, reg))

      val actual = await(repository.retrieveRegistration("AC123456"))

      actual shouldBe Some(reg)
    }

    "return empty option when there is no corresponding registration object" in new Setup {

      await(setupCollection(repository, reg))

      val actual = await(repository.retrieveCompanyDetails("AC654321"))

      actual shouldBe None
    }

    "retrieve company details" in new Setup {

      await(setupCollection(repository, reg))

      val actual = await(repository.retrieveCompanyDetails("AC123456"))

      actual shouldBe Some(details)
    }

    "upsert company details when there is no existing Company Details object" in new Setup {

      await(setupCollection(repository, regNoCompanyDetails))

      val actual = await(repository.upsertCompanyDetails("AC123456", details2))
      actual shouldBe details2

      val updated = await(repository.retrieveRegistration("AC123456"))
      updated shouldBe Some(regUpdatedCompanyDetails)

    }

    "upsert company details when the Registration already contains a Company Details object" in new Setup {

      await(setupCollection(repository, reg))

      val actual = await(repository.upsertCompanyDetails("AC123456", details2))
      actual shouldBe details2

      val updated = await(repository.retrieveRegistration("AC123456"))
      updated shouldBe Some(regUpdatedCompanyDetails)

    }

    "drop the collection" in new Setup {

      await(setupCollection(repository, reg))
      await(repository.dropCollection)

      val actual = await(repository.retrieveCompanyDetails("AC123456"))
      actual shouldBe None
    }
  }

}
