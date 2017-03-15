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
import enums.PAYEStatus
import models._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import play.api.Play
import reactivemongo.api.commands.WriteResult
import services.MetricsService
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class RegistrationMongoRepositoryISpec
  extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with Eventually with WithFakeApplication {

  private val date = LocalDate.of(2016, 12, 20)
  private val address = Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), Some("UK"))
  private val ppobAddress = Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK"))
  private val businessContact = DigitalContactDetails(Some("test@email.com"), Some("012345"), Some("543210"))
  private val companyDetails: CompanyDetails = CompanyDetails(crn = None, companyName = "tstCcompany", tradingName = Some("tstTradingName"), roAddress = address, ppobAddress = ppobAddress, businessContactDetails = businessContact)
  private val employmentDetails: Employment = Employment(employees = false, companyPension = None, subcontractors = false, date)
  private val reg = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", status = PAYEStatus.draft, completionCapacity = None, companyDetails = Some(companyDetails), directors = Seq.empty, payeContact = None, None, sicCodes = Seq.empty)
  private val reg2 = PAYERegistration(registrationID = "AC234567", internalID = "09876", formCreationTimestamp = "timestamp", status = PAYEStatus.held, completionCapacity = None, companyDetails = Some(companyDetails), directors = Seq.empty, payeContact = None, None, sicCodes = Seq.empty)

  // Company Details
  private val regNoCompanyDetails = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", status = PAYEStatus.draft, completionCapacity = None, companyDetails = None, directors = Seq.empty, payeContact = None, None, sicCodes = Seq.empty)
  private val companyDetails2: CompanyDetails = CompanyDetails(crn = None, companyName = "tstCcompany2", tradingName = Some("tstTradingName2"), roAddress = address, ppobAddress = ppobAddress, businessContactDetails = businessContact)
  private val regUpdatedCompanyDetails = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", status = PAYEStatus.draft, completionCapacity = None, companyDetails = Some(companyDetails2), directors = Seq.empty, payeContact = None, None, sicCodes = Seq.empty)

  // Employment
  private val regNoEmployment = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", status = PAYEStatus.draft, completionCapacity = None, companyDetails = Some(companyDetails), directors = Seq.empty, payeContact = None, None, sicCodes = Seq.empty)
  private val employmentDetails2: Employment = Employment(employees = true, companyPension = Some(false), subcontractors = true, date)
  private val regUpdatedEmployment = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", status = PAYEStatus.draft, completionCapacity = None, companyDetails = Some(companyDetails), directors = Seq.empty, payeContact = None, Some(employmentDetails2), sicCodes = Seq.empty)

  // Directors
  private val regNoDirectors = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", status = PAYEStatus.draft, completionCapacity = None, companyDetails = Some(companyDetails), directors = Seq.empty, payeContact = None, None, sicCodes = Seq.empty)
  private val directors: Seq[Director] = Seq(
    Director(
      Name(
        forename = Some("Thierry"),
        otherForenames = Some("Dominique"),
        surname = Some("Henry"),
        title = Some("Sir")
      ),
      Some("SR123456C")
    ),
    Director(
      Name(
        forename = Some("David"),
        otherForenames = Some("Jesus"),
        surname = Some("Trezeguet"),
        title = Some("Mr")
      ),
      Some("SR000009C")
    )
  )
  private val regUpdatedDirectors = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", status = PAYEStatus.draft, completionCapacity = None, companyDetails = Some(companyDetails), directors = directors, payeContact = None, None, sicCodes = Seq.empty)

  //SIC Codes
  private val regNoSICCodes = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", status = PAYEStatus.draft, completionCapacity = None, companyDetails = Some(companyDetails), directors = Seq.empty, payeContact = None, None, sicCodes = Seq.empty)
  private val sicCodes: Seq[SICCode] = Seq(
    SICCode(code = Some("123"), description = Some("consulting")),
    SICCode(code = None, description = Some("something"))
  )
  private val regUpdatedSICCodes = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", status = PAYEStatus.draft, completionCapacity = None, companyDetails = Some(companyDetails), directors = Seq.empty, payeContact = None, None, sicCodes = sicCodes)

  //PAYE Contact
  private val regNoPAYEContact = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", status = PAYEStatus.draft, completionCapacity = None, companyDetails = Some(companyDetails), directors = Seq.empty, payeContact = None, None, sicCodes = Seq.empty)
  private val payeContact = PAYEContact(
    contactDetails = PAYEContactDetails(
      name = "toto tata",
      digitalContactDetails = DigitalContactDetails(
        Some("test@test.com"),
        Some("1234"),
        Some("09876")
      )
    ),
    correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK"))
  )
  private val regUpdatedPAYEContact = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", status = PAYEStatus.draft, completionCapacity = None, companyDetails = Some(companyDetails), directors = Seq.empty, payeContact = Some(payeContact), None, sicCodes = Seq.empty)

  //Completion Capacity
  private val regNoCompletionCapacity = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", status = PAYEStatus.draft, completionCapacity = None, companyDetails = Some(companyDetails), directors = Seq.empty, payeContact = None, None, sicCodes = Seq.empty)
  private val completionCapacity = "Director"
  private val regUpdatedCompletionCapacity = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", status = PAYEStatus.draft, completionCapacity = Some(completionCapacity), companyDetails = Some(companyDetails), directors = Seq.empty, payeContact = None, None, sicCodes = Seq.empty)

  //Registration Status
  private val regUpdatedRegistrationStatus = PAYERegistration(registrationID = "AC123456", internalID = "09876", formCreationTimestamp = "timestamp", status = PAYEStatus.held, completionCapacity = None, companyDetails = Some(companyDetails), directors = Seq.empty, payeContact = None, None, sicCodes = Seq.empty)

  class Setup {
    lazy val mockMetrics = Play.current.injector.instanceOf[MetricsService]
    val mongo = new RegistrationMongo(mockMetrics)
    val repository = mongo.store
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

    "return a None when there is no corresponding registration object" in new Setup {
      await(setupCollection(repository, reg))

      await(repository.retrieveRegistration("AC654321")) shouldBe None
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

      await(setupCollection(repository, regUpdatedEmployment))

      val actual = await(repository.retrieveEmployment("AC123456"))

      actual shouldBe Some(employmentDetails2)
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

  "Calling retrieveDirectors" should {

    "retrieve directors" in new Setup {

      await(setupCollection(repository, regUpdatedDirectors))

      val actual = await(repository.retrieveDirectors("AC123456"))

      actual shouldBe directors
    }

    "return a MissingRegDocument when there is no corresponding PAYE Registration in the database" in new Setup {

      intercept[MissingRegDocument](await(repository.retrieveDirectors("AC123456")))
    }
  }

  "Calling upsertDirectors" should {

    "upsert directors when the list is empty" in new Setup {

      await(setupCollection(repository, regNoDirectors))

      val actual = await(repository.upsertDirectors("AC123456", directors))
      actual shouldBe directors

      val updated = await(repository.retrieveRegistration("AC123456"))
      updated shouldBe Some(regUpdatedDirectors)

    }

    "throw a Missing Reg Document exception when updating employment for a nonexistent registration" in new Setup {

      a[MissingRegDocument] shouldBe thrownBy(await(repository.upsertDirectors("AC123456", directors)))

    }
  }

  "Calling retrieveSICCodes" should {

    "retrieve sic codes" in new Setup {

      await(setupCollection(repository, regUpdatedSICCodes))

      val actual = await(repository.retrieveSICCodes("AC123456"))

      actual shouldBe sicCodes
    }

    "return a MissingRegDocument when there is no corresponding PAYE Registration in the database" in new Setup {

      intercept[MissingRegDocument](await(repository.retrieveSICCodes("AC123456")))
    }
  }

  "Calling upsertSICCodes" should {

    "upsert sic codes when the list is empty" in new Setup {

      await(setupCollection(repository, regNoSICCodes))

      val actual = await(repository.upsertSICCodes("AC123456", sicCodes))
      actual shouldBe sicCodes

      val updated = await(repository.retrieveRegistration("AC123456"))
      updated shouldBe Some(regUpdatedSICCodes)

    }

    "throw a Missing Reg Document exception when updating employment for a nonexistent registration" in new Setup {

      a[MissingRegDocument] shouldBe thrownBy(await(repository.upsertSICCodes("AC123456", sicCodes)))

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

    "insert a Registration when none exists" in new Setup {
      val actual = await(repository.updateRegistration(reg))
      actual shouldBe reg
    }

    "correctly update a registration that already exists" in new Setup {
      await(setupCollection(repository, reg))

      val actual = await(repository.updateRegistration(reg))
      actual shouldBe reg
    }
  }

  "Calling retrievePAYEContact" should {

    "retrieve paye contact" in new Setup {

      await(setupCollection(repository, regUpdatedPAYEContact))

      val actual = await(repository.retrievePAYEContact("AC123456"))

      actual shouldBe Some(payeContact)
    }

    "return a MissingRegDocument when there is no corresponding PAYE Registration in the database" in new Setup {
      intercept[MissingRegDocument](await(repository.retrievePAYEContact("AC123456")))
    }
  }

  "Calling upsertPAYEContact" should {

    "upsert paye contact" in new Setup {

      await(setupCollection(repository, regNoPAYEContact))

      val actual = await(repository.upsertPAYEContact("AC123456", payeContact))
      actual shouldBe payeContact

      val updated = await(repository.retrieveRegistration("AC123456"))
      updated shouldBe Some(regUpdatedPAYEContact)

    }

    "throw a Missing Reg Document exception when updating company details for a nonexistent registration" in new Setup {

      a[MissingRegDocument] shouldBe thrownBy(await(repository.upsertPAYEContact("AC123456", payeContact)))

    }
  }

  "Calling retrieveCompletionCapacity" should {

    "retrieve completion capacity" in new Setup {

      await(setupCollection(repository, regUpdatedCompletionCapacity))

      val actual = await(repository.retrieveCompletionCapacity("AC123456"))

      actual shouldBe Some(completionCapacity)
    }

    "return a MissingRegDocument when there is no corresponding PAYE Registration in the database" in new Setup {
      intercept[MissingRegDocument](await(repository.retrieveCompletionCapacity("AC123456")))
    }
  }

  "Calling upsertCompletionCapacity" should {

    "upsert completion capacity" in new Setup {

      await(setupCollection(repository, regNoCompletionCapacity))

      val actual = await(repository.upsertCompletionCapacity("AC123456", completionCapacity))
      actual shouldBe completionCapacity

      val updated = await(repository.retrieveRegistration("AC123456"))
      updated shouldBe Some(regUpdatedCompletionCapacity)

    }

    "throw a Missing Reg Document exception when updating company details for a nonexistent registration" in new Setup {

      a[MissingRegDocument] shouldBe thrownBy(await(repository.upsertCompletionCapacity("AC123456", completionCapacity)))

    }
  }


  "Calling retrieveRegistrationStatus" should {
    "retrieve registration status" in new Setup {
      await(setupCollection(repository, reg))

      val actual = await(repository.retrieveRegistrationStatus("AC123456"))
      actual shouldBe PAYEStatus.draft
    }

    "return a MissingRegDocument when there is no corresponding PAYE Registration in the database" in new Setup {
      intercept[MissingRegDocument](await(repository.retrieveCompletionCapacity("AC123456")))
    }
  }

  "Calling updateRegistrationStatus" should {
    "update registration status" in new Setup {
      await(setupCollection(repository, reg))

      val actual = await(repository.updateRegistrationStatus("AC123456", PAYEStatus.held))
      actual shouldBe PAYEStatus.held

      val updated = await(repository.retrieveRegistration("AC123456"))
      updated shouldBe Some(regUpdatedRegistrationStatus)
    }

    "throw a Missing Reg Document exception when updating registration status for a nonexistent registration" in new Setup {
      a[MissingRegDocument] shouldBe thrownBy(await(repository.updateRegistrationStatus("AC123456", PAYEStatus.held)))

    }
  }

}
