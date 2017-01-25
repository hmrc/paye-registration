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

import java.time.LocalDate

import common.exceptions.DBExceptions.{InsertFailed, MissingRegDocument}
import models._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import reactivemongo.api.commands.WriteResult
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class RegistrationMongoRepositoryISpec
  extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with Eventually with WithFakeApplication {

  private val date = LocalDate.of(2016, 12, 20)
  private val companyDetails: CompanyDetails = CompanyDetails(crn = None, companyName = "tstCcompany", tradingName = Some("tstTradingName"))
  private val employmentDetails: Employment = Employment(employees = false, companyPension = None, subcontractors = false, FirstPayment(true, date))
  private val reg = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", companyDetails = Some(companyDetails), Some(employmentDetails)) //employment =
  private val reg2 = PAYERegistration(registrationID = "AC234567", internalID = "09876", formCreationTimestamp = "timestamp", companyDetails = Some(companyDetails), None)

  // Company Details
  private val regNoCompanyDetails = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", companyDetails = None, Some(employmentDetails))
  private val companyDetails2: CompanyDetails = CompanyDetails(crn = None, companyName = "tstCcompany2", tradingName = Some("tstTradingName2"))
  private val regUpdatedCompanyDetails = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", companyDetails = Some(companyDetails2), Some(employmentDetails))

  // Employment
  private val regNoEmployment = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", companyDetails = Some(companyDetails), None)
  private val employmentDetails2: Employment = Employment(employees = true, companyPension = Some(false), subcontractors = true, FirstPayment(true, date))
  private val regUpdatedEmployment = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", companyDetails = Some(companyDetails), Some(employmentDetails2))

  class Setup {
    val repository = RegistrationMongo.store
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  def setupCollection(repo: RegistrationMongoRepository, registration: PAYERegistration): Future[WriteResult] = {
    repo.insert(registration)
  }

  "Calling createNewRegistration" should {

    "create a new, blank PAYERegistration with the correct ID" in new Setup {

      val actual = await(repository.createNewRegistration("AC234321", "09876"))
      actual.registrationID shouldBe "AC234321"

    }

    "throw an Insert Failed exception when creating a new PAYE reg when one already exists" in new Setup {
      await(setupCollection(repository, reg))

      an[InsertFailed] shouldBe thrownBy(await(repository.createNewRegistration(reg.registrationID, reg.internalID)))

    }
  }

  "Calling retrieveRegistration" should {

    "retrieve a registration object" in new Setup {
      await(setupCollection(repository, reg))

      val actual = await(repository.retrieveRegistration("AC123456"))

      actual shouldBe Some(reg)
    }

    "return a MissingRegDocument when there is no corresponding registration object" in new Setup {
      await(setupCollection(repository, reg))

      intercept[MissingRegDocument](await(repository.retrieveCompanyDetails("AC654321")))
    }
  }

  "Calling retrieveCompanyDetails" should {

    "retrieve company details" in new Setup {

      await(setupCollection(repository, reg))

      val actual = await(repository.retrieveCompanyDetails("AC123456"))

      actual shouldBe Some(companyDetails)
    }

    "return a MissingRegDocument when there is no corresponding PAYE Registration in the database" in new Setup {
      intercept[MissingRegDocument](await(repository.retrieveCompanyDetails("AC123456")))
    }
  }

  "Calling upsertCompanyDetails" should {

    "upsert company details when there is no existing Company Details object" in new Setup {

      await(setupCollection(repository, regNoCompanyDetails))

      val actual = await(repository.upsertCompanyDetails("AC123456", companyDetails2))
      actual shouldBe companyDetails2

      val updated = await(repository.retrieveRegistration("AC123456"))
      updated shouldBe Some(regUpdatedCompanyDetails)

    }

    "upsert company details when the Registration already contains a Company Details object" in new Setup {

      await(setupCollection(repository, reg))

      val actual = await(repository.upsertCompanyDetails("AC123456", companyDetails2))
      actual shouldBe companyDetails2

      val updated = await(repository.retrieveRegistration("AC123456"))
      updated shouldBe Some(regUpdatedCompanyDetails)

    }

    "throw a Missing Reg Document exception when updating company details for a nonexistent registration" in new Setup {

      a[MissingRegDocument] shouldBe thrownBy(await(repository.upsertCompanyDetails("AC123456", companyDetails)))

    }
  }


  "Calling retrieveEmployment" should {

    "retrieve employment" in new Setup {

      await(setupCollection(repository, reg))

      val actual = await(repository.retrieveEmployment("AC123456"))

      actual shouldBe Some(employmentDetails)
    }

    "return a MissingRegDocument when there is no corresponding PAYE Registration in the database" in new Setup {

      intercept[MissingRegDocument](await(repository.retrieveEmployment("AC123456")))
    }
  }

  "Calling upsertEmployment" should {

    "upsert employment details when there is no existing Employment object" in new Setup {

      await(setupCollection(repository, regNoEmployment))

      val actual = await(repository.upsertEmployment("AC123456", employmentDetails2))
      actual shouldBe employmentDetails2

      val updated = await(repository.retrieveRegistration("AC123456"))
      updated shouldBe Some(regUpdatedEmployment)

    }

    "upsert employment details when the Registration already contains an Employment object" in new Setup {

      await(setupCollection(repository, reg))

      val actual = await(repository.upsertEmployment("AC123456", employmentDetails2))
      actual shouldBe employmentDetails2

      val updated = await(repository.retrieveRegistration("AC123456"))
      updated shouldBe Some(regUpdatedEmployment)

    }

    "throw a Missing Reg Document exception when updating employment for a nonexistent registration" in new Setup {

      a[MissingRegDocument] shouldBe thrownBy(await(repository.upsertEmployment("AC123456", employmentDetails)))

    }
  }

  "Test setup functions" should {

    "drop the collection" in new Setup {

      await(setupCollection(repository, reg))
      await(repository.dropCollection)

      intercept[MissingRegDocument](await(repository.retrieveCompanyDetails("AC123456")))
    }

    "delete a specific registration" in new Setup {
      await(setupCollection(repository, reg))
      await(setupCollection(repository, reg2))

      val actual = await(repository.deleteRegistration("AC123456"))
      actual shouldBe true

      val deletedFind = await(repository.retrieveRegistration("AC123456"))
      deletedFind shouldBe None

      val remaining = await(repository.retrieveRegistration("AC234567"))
      remaining shouldBe Some(reg2)
    }

    "insert a Registration" in new Setup {
      val actual = await(repository.addRegistration(reg))
      actual shouldBe reg
    }

    "throw the correct error when inserting a registration that already exists" in new Setup {
      await(setupCollection(repository, reg))

      an[InsertFailed] shouldBe thrownBy(await(repository.addRegistration(reg)))
    }
  }

}
