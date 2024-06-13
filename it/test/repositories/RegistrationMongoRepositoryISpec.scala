/*
 * Copyright 2023 HM Revenue & Customs
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

import auth.CryptoSCRS
import com.codahale.metrics.MetricRegistry
import common.exceptions.DBExceptions.{InsertFailed, MissingRegDocument}
import common.exceptions.RegistrationExceptions.AcknowledgementReferenceExistsException
import enums.{Employing, PAYEStatus}
import helpers.DateHelper
import itutil.MongoBaseSpec
import models._
import org.mongodb.scala.model.Filters
import play.api.Configuration
import play.api.test.Helpers._
import utils.SystemDate

import java.time._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RegistrationMongoRepositoryISpec extends MongoBaseSpec {

  private val date = LocalDate.of(2016, 12, 20)
  private val lastUpdate = "2017-05-09T07:58:35Z"
  private val lastUpdateZDT = ZonedDateTime.of(LocalDateTime.of(2017, 5, 9, 7, 58, 35), ZoneId.of("Z"))

  private val address = Address(
    "14 St Test Walk",
    "Testley",
    Some("Testford"),
    Some("Testshire"),
    Some("TE1 1ST"),
    None
  )

  private val ppobAddress = Address(
    "15 St Walk",
    "Testley",
    Some("Testford"),
    Some("Testshire"),
    Some("TE4 1ST"),
    None,
    Some("auditRef")
  )
  private val businessContact = DigitalContactDetails(
    Some("test@email.com"),
    Some("012345"),
    Some("543210")
  )

  private val companyDetails: CompanyDetails = CompanyDetails(
    companyName = "tstCcompany",
    tradingName = Some("tstTradingName"),
    roAddress = address,
    ppobAddress = ppobAddress,
    businessContactDetails = businessContact
  )

  private val employmentDetails: EmploymentInfo = EmploymentInfo(
    employees = Employing.notEmploying,
    companyPension = None,
    construction = true,
    subcontractors = false,
    firstPaymentDate = SystemDate.getSystemDate.toLocalDate
  )

  private val reg = PAYERegistration(
    registrationID = "AC123456",
    transactionID = "NN1234",
    internalID = "09876",
    acknowledgementReference = Some("testAckRef"),
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "timestamp",
    status = PAYEStatus.draft,
    completionCapacity = None,
    companyDetails = Some(companyDetails),
    directors = Seq.empty,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(lastUpdateZDT),
    employmentInfo = None
  )

  private val reg2 = PAYERegistration(
    registrationID = "AC234567",
    transactionID = "NN5678",
    internalID = "09876",
    acknowledgementReference = Some("testAckRef"),
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "timestamp",
    status = PAYEStatus.held,
    completionCapacity = None,
    companyDetails = Some(companyDetails),
    directors = Seq.empty,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(lastUpdateZDT),
    employmentInfo = None
  )

  // Company Details
  private val regNoCompanyDetails = PAYERegistration(
    registrationID = "AC123456",
    transactionID = "NN1234",
    internalID = "09876",
    acknowledgementReference = Some("testAckRef"),
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "timestamp",
    status = PAYEStatus.draft,
    completionCapacity = None,
    companyDetails = None,
    directors = Seq.empty,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(lastUpdateZDT),
    employmentInfo = None
  )

  private val companyDetails2: CompanyDetails = CompanyDetails(
    companyName = "tstCcompany2",
    tradingName = Some("tstTradingName2"),
    roAddress = address,
    ppobAddress = ppobAddress,
    businessContactDetails = businessContact
  )

  private val regUpdatedCompanyDetails = PAYERegistration(
    registrationID = "AC123456",
    transactionID = "NN1234",
    internalID = "09876",
    acknowledgementReference = Some("testAckRef"),
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "timestamp",
    status = PAYEStatus.draft,
    completionCapacity = None,
    companyDetails = Some(companyDetails2),
    directors = Seq.empty,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(lastUpdateZDT),
    employmentInfo = None
  )

  // Employment
  private val regNoEmployment = PAYERegistration(
    registrationID = "AC123456",
    transactionID = "NN1234",
    internalID = "09876",
    acknowledgementReference = Some("testAckRef"),
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "timestamp",
    status = PAYEStatus.draft,
    completionCapacity = None,
    companyDetails = Some(companyDetails),
    directors = Seq.empty,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(lastUpdateZDT),
    employmentInfo = None
  )
  private val employmentDetails2: EmploymentInfo = EmploymentInfo(
    employees = Employing.alreadyEmploying,
    companyPension = Some(false),
    construction = true,
    subcontractors = true,
    firstPaymentDate = date
  )

  private val regUpdatedEmployment = PAYERegistration(
    registrationID = "AC123456",
    transactionID = "NN1234",
    internalID = "09876",
    acknowledgementReference = Some("testAckRef"),
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "timestamp",
    status = PAYEStatus.draft,
    completionCapacity = None,
    companyDetails = Some(companyDetails),
    directors = Seq.empty,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(lastUpdateZDT),
    employmentInfo = Some(employmentDetails2)
  )

  // Directors
  private val regNoDirectors = PAYERegistration(
    registrationID = "AC123456",
    transactionID = "NN1234",
    internalID = "09876",
    acknowledgementReference = Some("testAckRef"),
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "timestamp",
    status = PAYEStatus.draft,
    completionCapacity = None,
    companyDetails = Some(companyDetails),
    directors = Seq.empty,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(lastUpdateZDT),
    employmentInfo = None
  )

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
  private val regUpdatedDirectors = PAYERegistration(
    registrationID = "AC123456",
    transactionID = "NN1234",
    internalID = "09876",
    acknowledgementReference = Some("testAckRef"),
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "timestamp",
    status = PAYEStatus.draft,
    completionCapacity = None,
    companyDetails = Some(companyDetails),
    directors = directors,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(lastUpdateZDT),
    employmentInfo = None
  )

  //SIC Codes
  private val regNoSICCodes = PAYERegistration(
    registrationID = "AC123456",
    transactionID = "NN1234",
    internalID = "09876",
    acknowledgementReference = Some("testAckRef"),
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "timestamp",
    status = PAYEStatus.draft,
    completionCapacity = None,
    companyDetails = Some(companyDetails),
    directors = Seq.empty,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(lastUpdateZDT),
    employmentInfo = None
  )

  private val sicCodes: Seq[SICCode] = Seq(
    SICCode(code = Some("123"), description = Some("consulting")),
    SICCode(code = None, description = Some("something"))
  )

  private val regUpdatedSICCodes = PAYERegistration(
    registrationID = "AC123456",
    transactionID = "NN1234",
    internalID = "09876",
    acknowledgementReference = Some("testAckRef"),
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "timestamp",
    status = PAYEStatus.draft,
    completionCapacity = None,
    companyDetails = Some(companyDetails),
    directors = Seq.empty,
    payeContact = None,
    sicCodes = sicCodes,
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(lastUpdateZDT),
    employmentInfo = None
  )

  //PAYE Contact
  private val regNoPAYEContact = PAYERegistration(
    registrationID = "AC123456",
    transactionID = "NN1234",
    internalID = "09876",
    acknowledgementReference = Some("testAckRef"),
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "timestamp",
    status = PAYEStatus.draft,
    completionCapacity = None,
    companyDetails = Some(companyDetails),
    directors = Seq.empty,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(lastUpdateZDT),
    employmentInfo = None
  )

  private val payeContact = PAYEContact(
    contactDetails = PAYEContactDetails(
      name = "toto tata",
      digitalContactDetails = DigitalContactDetails(
        Some("test@test.com"),
        Some("1234"),
        Some("09876")
      )
    ),
    correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), Some("TE4 1ST"))
  )

  private val regUpdatedPAYEContact = PAYERegistration(
    registrationID = "AC123456",
    transactionID = "NN1234",
    internalID = "09876",
    acknowledgementReference = Some("testAckRef"),
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "timestamp",
    status = PAYEStatus.draft,
    completionCapacity = None,
    companyDetails = Some(companyDetails),
    directors = Seq.empty,
    payeContact = Some(payeContact),
    sicCodes = Seq.empty,
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(lastUpdateZDT),
    employmentInfo = None
  )

  //Completion Capacity
  private val regNoCompletionCapacity = PAYERegistration(
    registrationID = "AC123456",
    transactionID = "NN1234",
    internalID = "09876",
    acknowledgementReference = Some("testAckRef"),
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "timestamp",
    status = PAYEStatus.draft,
    completionCapacity = None,
    companyDetails = Some(companyDetails),
    directors = Seq.empty,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(lastUpdateZDT),
    employmentInfo = None
  )
  private val completionCapacity = "Director"

  private val regUpdatedCompletionCapacity = PAYERegistration(
    registrationID = "AC123456",
    transactionID = "NN1234",
    internalID = "09876",
    acknowledgementReference = Some("testAckRef"),
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "timestamp",
    status = PAYEStatus.draft,
    completionCapacity = Some(completionCapacity),
    companyDetails = Some(companyDetails),
    directors = Seq.empty,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(lastUpdateZDT),
    employmentInfo = None
  )

  //Registration Status
  private val regUpdatedRegistrationStatus = PAYERegistration(
    registrationID = "AC123456",
    transactionID = "NN1234",
    internalID = "09876",
    acknowledgementReference = Some("testAckRef"),
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "timestamp",
    status = PAYEStatus.held,
    completionCapacity = None,
    companyDetails = Some(companyDetails),
    directors = Seq.empty,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate,
    partialSubmissionTimestamp = Some(lastUpdate),
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(lastUpdateZDT),
    employmentInfo = None
  )
  val empInfo: EmploymentInfo = EmploymentInfo(Employing.alreadyEmploying, LocalDate.of(2018,4,9), true, true, Some(true))

  class Setup(timestampZDT: ZonedDateTime = lastUpdateZDT) {
    lazy val mockcryptoSCRS: CryptoSCRS = app.injector.instanceOf[CryptoSCRS]


    lazy val mockMetricRegistry: MetricRegistry = app.injector.instanceOf[MetricRegistry]
    lazy val mockDateHelper: DateHelper = new DateHelper {
      override def getTimestamp: ZonedDateTime = timestampZDT
    }
    lazy val sConfig: Configuration = app.injector.instanceOf[Configuration]
    val repository = new RegistrationMongoRepository(mockMetricRegistry, mockDateHelper, mongoComponent, sConfig, mockcryptoSCRS)

    await(repository.dropCollection)
  }

  def setupCollection(repo: RegistrationMongoRepository, registration: PAYERegistration): Future[PAYERegistration] = {
    repo.updateRegistration(registration)
  }

  "Calling createNewRegistration" should {

    "create a new, blank PAYERegistration with the correct ID" in new Setup {

      val actual: PAYERegistration = await(repository.createNewRegistration("AC234321", "NN1234", "09876"))
      actual.registrationID mustBe "AC234321"
      actual.transactionID mustBe "NN1234"
      actual.lastAction.isDefined mustBe true
    }

    "throw an Insert Failed exception when creating a new PAYE reg when one already exists" in new Setup {
      await(setupCollection(repository, reg))

      an[InsertFailed] mustBe thrownBy(await(repository.createNewRegistration(reg.registrationID, reg.transactionID, reg.internalID)))

    }
  }

  "Calling retrieveRegistration" should {

    "retrieve a registration object" in new Setup {
      await(setupCollection(repository, reg))

      val actual: Option[PAYERegistration] = await(repository.retrieveRegistration("AC123456"))

      actual mustBe Some(reg)
    }

    "return a None when there is no corresponding registration object" in new Setup {
      await(setupCollection(repository, reg))

      await(repository.retrieveRegistration("AC654321")) mustBe None
    }
  }

  "Calling retrieveRegistrationByTransactionID" should {

    "retrieve a registration object" in new Setup {
      await(setupCollection(repository, reg))

      val actual: Option[PAYERegistration] = await(repository.retrieveRegistrationByTransactionID("NN1234"))

      actual mustBe Some(reg)
    }

    "return a None when there is no corresponding registration object" in new Setup {
      await(setupCollection(repository, reg))

      await(repository.retrieveRegistrationByTransactionID("NN54456")) mustBe None
    }
  }

  "Calling retrieveCompanyDetails" should {

    "retrieve company details" in new Setup {

      await(setupCollection(repository, reg))

      val actual: Option[CompanyDetails] = await(repository.retrieveCompanyDetails("AC123456"))

      actual mustBe Some(companyDetails)
    }

    "return a MissingRegDocument when there is no corresponding PAYE Registration in the database" in new Setup {
      intercept[MissingRegDocument](await(repository.retrieveCompanyDetails("AC123456")))
    }
  }

  "Calling upsertCompanyDetails" should {

    "upsert company details when there is no existing Company Details object" in new Setup {

      await(setupCollection(repository, regNoCompanyDetails))

      val actual: CompanyDetails = await(repository.upsertCompanyDetails("AC123456", companyDetails2))
      actual mustBe companyDetails2

      val updated: Option[PAYERegistration] = await(repository.retrieveRegistration("AC123456"))
      updated mustBe Some(regUpdatedCompanyDetails)

    }

    "upsert company details when the Registration already contains a Company Details object" in new Setup {

      await(setupCollection(repository, reg))

      val actual: CompanyDetails = await(repository.upsertCompanyDetails("AC123456", companyDetails2))
      actual mustBe companyDetails2

      val updated: Option[PAYERegistration] = await(repository.retrieveRegistration("AC123456"))
      updated mustBe Some(regUpdatedCompanyDetails)

    }

    "throw a Missing Reg Document exception when updating company details for a nonexistent registration" in new Setup {

      a[MissingRegDocument] mustBe thrownBy(await(repository.upsertCompanyDetails("AC123456", companyDetails)))

    }
  }

  "calling retrieveEmploymentInfo" should {

    val payeReg: PAYERegistration = reg.copy(employmentInfo = Some(empInfo))

    "return EmploymentInfo" in new Setup {
      await(setupCollection(repository, payeReg))

      val res: Option[EmploymentInfo] = await(repository.retrieveEmploymentInfo(payeReg.registrationID))
      res mustBe Some(empInfo)
    }
    "return None when there is no EmploymentInfo in mongo" in new Setup {
      await(setupCollection(repository, reg.copy(employmentInfo = None)))

      val res: Option[EmploymentInfo] = await(repository.retrieveEmploymentInfo(reg.registrationID))
      res mustBe None
    }
  }

  "calling upsertEmploymentInfo" should {
    "upsert successfully when a reg doc already exists and Employment Info does not exist" in new Setup {
      await(setupCollection(repository, reg.copy(employmentInfo = None)))

      val res: EmploymentInfo = await(repository.upsertEmploymentInfo(reg.registrationID, empInfo))
      res mustBe empInfo

      val updated: Option[EmploymentInfo] = await(repository.retrieveEmploymentInfo(reg.registrationID))
      updated mustBe Some(empInfo)
    }

    "modify an existing EmploymentInfo successfully and also read with invalid date according to APIValidation" in new Setup {
      val empInfoModified: EmploymentInfo = EmploymentInfo(
        employees        = Employing.alreadyEmploying,
        firstPaymentDate = LocalDate.of(1934,1,1),
        construction     = false,
        subcontractors   = false,
        companyPension   = None
      )
      await(setupCollection(repository, reg.copy(employmentInfo = Some(empInfo))))

      val res: EmploymentInfo = await(repository.upsertEmploymentInfo(reg.registrationID, empInfoModified))
      res mustBe empInfoModified

      val updated: Option[EmploymentInfo] = await(repository.retrieveEmploymentInfo(reg.registrationID))
      updated mustBe Some(empInfoModified)
    }
  }

  "Calling retrieveDirectors" should {

    "retrieve directors" in new Setup {

      await(setupCollection(repository, regUpdatedDirectors))

      val actual: Seq[Director] = await(repository.retrieveDirectors("AC123456"))

      actual mustBe directors
    }

    "return a MissingRegDocument when there is no corresponding PAYE Registration in the database" in new Setup {

      intercept[MissingRegDocument](await(repository.retrieveDirectors("AC123456")))
    }
  }

  "Calling upsertDirectors" should {

    "upsert directors when the list is empty" in new Setup {

      await(setupCollection(repository, regNoDirectors))

      val actual: Seq[Director] = await(repository.upsertDirectors("AC123456", directors))
      actual mustBe directors

      val updated: Option[PAYERegistration] = await(repository.retrieveRegistration("AC123456"))
      updated mustBe Some(regUpdatedDirectors)

    }

    "throw a Missing Reg Document exception when updating employment for a nonexistent registration" in new Setup {

      a[MissingRegDocument] mustBe thrownBy(await(repository.upsertDirectors("AC123456", directors)))

    }
  }

  "Calling retrieveSICCodes" should {

    "retrieve sic codes" in new Setup {

      await(setupCollection(repository, regUpdatedSICCodes))

      val actual: Seq[SICCode] = await(repository.retrieveSICCodes("AC123456"))

      actual mustBe sicCodes
    }

    "return a MissingRegDocument when there is no corresponding PAYE Registration in the database" in new Setup {

      intercept[MissingRegDocument](await(repository.retrieveSICCodes("AC123456")))
    }
  }

  "Calling upsertSICCodes" should {

    "upsert sic codes when the list is empty" in new Setup {

      await(setupCollection(repository, regNoSICCodes))

      val actual: Seq[SICCode] = await(repository.upsertSICCodes("AC123456", sicCodes))
      actual mustBe sicCodes

      val updated: Option[PAYERegistration] = await(repository.retrieveRegistration("AC123456"))
      updated mustBe Some(regUpdatedSICCodes)

    }

    "throw a Missing Reg Document exception when updating employment for a nonexistent registration" in new Setup {

      a[MissingRegDocument] mustBe thrownBy(await(repository.upsertSICCodes("AC123456", sicCodes)))

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

      val actual: Boolean = await(repository.deleteRegistration("AC123456"))
      actual mustBe true

      val deletedFind: Option[PAYERegistration] = await(repository.retrieveRegistration("AC123456"))
      deletedFind mustBe None

      val remaining: Option[PAYERegistration] = await(repository.retrieveRegistration("AC234567"))
      remaining mustBe Some(reg2)
    }

    "insert a Registration when none exists" in new Setup {
      val actual: PAYERegistration = await(repository.updateRegistration(reg))
      actual mustBe reg
    }

    "correctly update a registration that already exists" in new Setup {
      await(setupCollection(repository, reg))

      val actual: PAYERegistration = await(repository.updateRegistration(reg))
      actual mustBe reg
    }
  }

  "Calling retrievePAYEContact" should {

    "retrieve paye contact" in new Setup {

      await(setupCollection(repository, regUpdatedPAYEContact))

      val actual: Option[PAYEContact] = await(repository.retrievePAYEContact("AC123456"))

      actual mustBe Some(payeContact)
    }

    "return a MissingRegDocument when there is no corresponding PAYE Registration in the database" in new Setup {
      intercept[MissingRegDocument](await(repository.retrievePAYEContact("AC123456")))
    }
  }

  "Calling upsertPAYEContact" should {

    "upsert paye contact" in new Setup {

      await(setupCollection(repository, regNoPAYEContact))

      val actual: PAYEContact = await(repository.upsertPAYEContact("AC123456", payeContact))
      actual mustBe payeContact

      val updated: Option[PAYERegistration] = await(repository.retrieveRegistration("AC123456"))
      updated mustBe Some(regUpdatedPAYEContact)

    }

    "throw a Missing Reg Document exception when updating company details for a nonexistent registration" in new Setup {

      a[MissingRegDocument] mustBe thrownBy(await(repository.upsertPAYEContact("AC123456", payeContact)))

    }
  }

  "Calling retrieveCompletionCapacity" should {

    "retrieve completion capacity" in new Setup {

      await(setupCollection(repository, regUpdatedCompletionCapacity))

      val actual: Option[String] = await(repository.retrieveCompletionCapacity("AC123456"))

      actual mustBe Some(completionCapacity)
    }

    "return a MissingRegDocument when there is no corresponding PAYE Registration in the database" in new Setup {
      intercept[MissingRegDocument](await(repository.retrieveCompletionCapacity("AC123456")))
    }
  }

  "Calling upsertCompletionCapacity" should {

    "upsert completion capacity" in new Setup {

      await(setupCollection(repository, regNoCompletionCapacity))

      val actual: String = await(repository.upsertCompletionCapacity("AC123456", completionCapacity))
      actual mustBe completionCapacity

      val updated: Option[PAYERegistration] = await(repository.retrieveRegistration("AC123456"))
      updated mustBe Some(regUpdatedCompletionCapacity)

    }

    "throw a Missing Reg Document exception when updating company details for a nonexistent registration" in new Setup {

      a[MissingRegDocument] mustBe thrownBy(await(repository.upsertCompletionCapacity("AC123456", completionCapacity)))

    }
  }


  "Calling retrieveRegistrationStatus" should {
    "retrieve registration status" in new Setup {
      await(setupCollection(repository, reg))

      val actual: PAYEStatus.Value = await(repository.retrieveRegistrationStatus("AC123456"))
      actual mustBe PAYEStatus.draft
    }

    "return a MissingRegDocument when there is no corresponding PAYE Registration in the database" in new Setup {
      intercept[MissingRegDocument](await(repository.retrieveCompletionCapacity("AC123456")))
    }
  }

  "Calling updateRegistrationStatus" should {
    "update registration status" in new Setup {
      await(setupCollection(repository, reg))

      val actual: PAYEStatus.Value = await(repository.updateRegistrationStatus("AC123456", PAYEStatus.held))
      actual mustBe PAYEStatus.held

      val updated: Option[PAYERegistration] = await(repository.retrieveRegistration("AC123456"))
      updated mustBe Some(regUpdatedRegistrationStatus)
    }

    "throw a Missing Reg Document exception when updating registration status for a nonexistent registration" in new Setup {
      a[MissingRegDocument] mustBe thrownBy(await(repository.updateRegistrationStatus("AC123456", PAYEStatus.held)))

    }
  }

  "Calling retrieveAcknowledgementReference" should {
    "get the acknowledgement reference from ther registration document" in new Setup {
      await(setupCollection(repository, reg))

      val result: Option[String] = await(repository.retrieveAcknowledgementReference("AC123456"))
      result mustBe Some("testAckRef")
    }

    "throw a missing document exception" in new Setup {
      intercept[MissingRegDocument](await(repository.retrieveAcknowledgementReference("INVALID_ACK_REF")))
    }
  }

  "Calling saveAcknowledgementReference" should {
    "update the registration document with the given ackref" in new Setup {
      await(setupCollection(repository, reg.copy(acknowledgementReference = None)))

      val result: String = await(repository.saveAcknowledgementReference(reg.registrationID, "testAckRef"))
      result mustBe "testAckRef"
    }

    "throw a new AcknowledgementReferenceExistsException" in new Setup {
      await(setupCollection(repository, reg))

      intercept[AcknowledgementReferenceExistsException](await(repository.saveAcknowledgementReference(reg.registrationID, "testAckRef")))
    }

    "throw a MissingRegDocumentException" in new Setup {
      intercept[MissingRegDocument](await(repository.saveAcknowledgementReference("INVALID_REG_ID", "testAckRef")))
    }
  }

  val clearedRegistration: PAYERegistration = PAYERegistration(
    registrationID = "AC123456",
    transactionID = "NN1234",
    internalID = "09876",
    acknowledgementReference = Some("testAckRef"),
    crn = None,
    registrationConfirmation = None,
    formCreationTimestamp = "timestamp",
    status = PAYEStatus.held,
    completionCapacity = None,
    companyDetails = None,
    directors = Seq.empty,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(lastUpdateZDT),
    employmentInfo = None
  )

  "Calling cleardownRegistration" should {
    "clear all information apart from Status and Ackref" in new Setup {
      await(setupCollection(repository, reg.copy(
        status = PAYEStatus.held,
        employmentInfo = Some(empInfo)))
      )

      val result: PAYERegistration = await(repository.cleardownRegistration(reg.registrationID))
      result mustBe clearedRegistration
    }
  }

  "updateRegistrationEmpRef" should {
    "return the emp ref notif" when {
      "the reg doc has been updated" in new Setup {
        await(setupCollection(repository, reg.copy(registrationConfirmation = None)))

        val testEmpRefNotif: EmpRefNotification = EmpRefNotification(Some("testEmpRef"), "2017-01-01T21:00:00Z", "04")

        val alteredReg: PAYERegistration = reg.copy(registrationConfirmation = Some(testEmpRefNotif), status = PAYEStatus.submitted)

        val result: EmpRefNotification = await(repository.updateRegistrationEmpRef("testAckRef", PAYEStatus.submitted, testEmpRefNotif))
        result mustBe testEmpRefNotif

        await(repository.retrieveRegistration(reg.registrationID)) mustBe Some(alteredReg)
      }
    }

    "throw a missing document exception" when {
      "the reg doc cannot be found against the given regId" in new Setup {
        val testEmpRefNotif: EmpRefNotification = EmpRefNotification(Some("testEmpRef"), "2017-01-01T21:00:00Z", "04")
        intercept[MissingRegDocument](await(repository.updateRegistrationEmpRef("AC123456", PAYEStatus.submitted, testEmpRefNotif)))
      }
    }
  }

  "Calling deleteRegistration" should {
    "returns true after deleting the registration from the collection" in new Setup {
      await(setupCollection(repository, reg.copy(status = PAYEStatus.rejected, acknowledgedTimestamp = Some("2017-01-01T21:00:00Z"))))

      await(repository.deleteRegistration(reg.registrationID)) mustBe true
      await(repository.retrieveRegistration(reg.registrationID)) mustBe None
    }
  }

  "Calling removeStaleDocuments" should {

    def timedReg(regId: String, lastAction: Option[ZonedDateTime], status: PAYEStatus.Value = PAYEStatus.draft) = PAYERegistration(
      registrationID = regId,
      transactionID = s"100-000-$regId",
      internalID = "09876",
      acknowledgementReference = Some("testAckRef"),
      crn = None,
      registrationConfirmation = None,
      formCreationTimestamp = "timestamp",
      status = status,
      completionCapacity = None,
      companyDetails = None,
      directors = Seq.empty,
      payeContact = None,
      sicCodes = Seq.empty,
      lastUpdate,
      partialSubmissionTimestamp = None,
      fullSubmissionTimestamp = None,
      acknowledgedTimestamp = None,
      lastAction = lastAction,
      employmentInfo = None
    )

    def dt = ZonedDateTime.of(LocalDateTime.of(2017, 6, 30, 12, 0, 0), ZoneId.of("Z"))

    "clear any documents older than 90 days" in new Setup(timestampZDT = dt) {
      val deleteDT: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2017, 4, 1, 12, 0, 0), ZoneId.of("Z"))
      val keepDT: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2017, 4, 1, 12, 0, 1), ZoneId.of("Z"))
      await(repository.updateRegistration(timedReg("123", Some(deleteDT))))
      await(repository.updateRegistration(timedReg("223", Some(keepDT))))
      await(repository.removeStaleDocuments())

      await(repository.retrieveRegistration("123")) mustBe None
      await(repository.retrieveRegistration("223")) mustBe Some(timedReg("223", Some(keepDT)))
    }

    "clear only documents that are draft or invalid" in new Setup(timestampZDT = dt) {
      val deleteDT: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2017, 4, 1, 12, 0, 0), ZoneId.of("Z"))
      await(repository.updateRegistration(timedReg("123", Some(deleteDT), PAYEStatus.draft)))
      await(repository.updateRegistration(timedReg("223", Some(deleteDT), PAYEStatus.invalid)))
      await(repository.updateRegistration(timedReg("323", Some(deleteDT), PAYEStatus.held)))
      await(repository.removeStaleDocuments())

      await(repository.retrieveRegistration("123")) mustBe None
      await(repository.retrieveRegistration("223")) mustBe None
      await(repository.retrieveRegistration("323")) mustBe Some(timedReg("323", Some(deleteDT), PAYEStatus.held))
    }

    "not clear documents which don't have a lastAction field" in new Setup(timestampZDT = dt) {
      await(repository.updateRegistration(timedReg("123", None, PAYEStatus.draft)))
      await(repository.removeStaleDocuments())
      await(repository.retrieveRegistration("123")) mustBe Some(timedReg("123", None, PAYEStatus.draft))
    }
  }

  "getRegistrationId" should {
    "return the registrationId" in new Setup {
      await(setupCollection(repository, reg))
      await(repository.getRegistrationId(reg.transactionID)) mustBe reg.registrationID
    }

    "throw a MissingRegDoc exception" in new Setup {
      intercept[MissingRegDocument](await(repository.getRegistrationId(reg.transactionID)))
    }
  }
}