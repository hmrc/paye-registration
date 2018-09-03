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

import java.time.LocalDate

import common.exceptions.DBExceptions.{MissingRegDocument, RetrieveFailed, UpdateFailed}
import common.exceptions.RegistrationExceptions._
import connectors.IncorporationInformationConnect
import enums.{Employing, PAYEStatus}
import fixtures.RegistrationFixture
import helpers.PAYERegSpec
import models._
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, contains, eq => eqTo}
import org.mockito.Mockito._
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId

class RegistrationServiceSpec extends PAYERegSpec with RegistrationFixture {
  val mockAuditService = mock[AuditService]
  val mockIncorporationInformationConnector = mock[IncorporationInformationConnect]

  class Setup {
    val service = new RegistrationSrv {
      override val registrationRepository = mockRegistrationRepository
      override val payeRestartURL = "testRestartURL"
      override val payeCancelURL = "testCancelURL"
      override val auditService = mockAuditService
      override val incorporationInformationConnector = mockIncorporationInformationConnector
    }
  }

  override def beforeEach() {
    reset(mockRegistrationRepository)
  }

  val regId = "AB123456"

  "Calling newPAYERegistration" should {

    "return a DBDuplicate response when the database already has a PAYERegistration" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(eqTo(validRegistration.registrationID))(any()))
        .thenReturn(Future.successful(Some(validRegistration)))

      val actual = await(service.createNewPAYERegistration(validRegistration.registrationID, validRegistration.transactionID, validRegistration.internalID))
      actual shouldBe validRegistration
    }

    "return a DBSuccess response when the Registration is correctly inserted into the database" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(contains("AC123456"))(any()))
        .thenReturn(Future.successful(None))
      when(mockRegistrationRepository.createNewRegistration(contains("AC123456"), contains("NNASD9789F"), any[String]())(any()))
        .thenReturn(Future.successful(validRegistration))

      val actual = await(service.createNewPAYERegistration("AC123456", "NNASD9789F", "09876"))
      actual shouldBe validRegistration
    }
  }

  "Calling fetchPAYERegistration" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(eqTo(regId))(any()))
        .thenReturn(Future.successful(None))

      val actual = await(service.fetchPAYERegistration(regId))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveRegistration(eqTo(regId))(any()))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.fetchPAYERegistration(regId)) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(eqTo(regId))(any()))
        .thenReturn(Future.successful(Some(validRegistration)))

      val actual = await(service.fetchPAYERegistration(regId))
      actual shouldBe Some(validRegistration)
    }
  }

  "Calling getCompanyDetails" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveCompanyDetails(eqTo(regId))(any()))
        .thenReturn(Future.successful(None))

      val actual = await(service.getCompanyDetails(regId))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveCompanyDetails(eqTo(regId))(any()))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getCompanyDetails(regId)) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveCompanyDetails(eqTo(regId))(any()))
        .thenReturn(Future.successful(Some(validCompanyDetails)))

      val actual = await(service.getCompanyDetails(regId))
      actual shouldBe validRegistration.companyDetails
    }
  }

  "Calling upsertCompanyDetails" should {

    "throw a MissingRegDocument exception when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertCompanyDetails(eqTo(regId), any[CompanyDetails]())(any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      intercept[MissingRegDocument] { await(service.upsertCompanyDetails(regId, validCompanyDetails)) }
    }

    "throw a RegistrationFormatException when the PAYE Contact has no contact method defined" in new Setup {
      val invalidCompanyDetails = validCompanyDetails.copy(businessContactDetails = DigitalContactDetails(None, None, None))
      intercept[RegistrationFormatException] { await(service.upsertCompanyDetails(regId, invalidCompanyDetails)) }
    }

    "return a Company Details Model when the company details are successfully updated" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.upsertCompanyDetails(eqTo(regId), any[CompanyDetails]())(any()))
        .thenReturn(Future.successful(validCompanyDetails))

      val actual = await(service.upsertCompanyDetails(regId, validCompanyDetails))
      actual shouldBe validCompanyDetails
    }
  }
  "Calling getEmploymentInfo" should {
    val empInfo = EmploymentInfo(Employing.alreadyEmploying, LocalDate.of(2018,4,9), true, true, Some(true))

    "return a EmploymentInfo model" in new Setup {
      when(mockRegistrationRepository.retrieveEmploymentInfo(eqTo(regId))(any()))
        .thenReturn(Future.successful(Some(empInfo)))

      await(service.getEmploymentInfo(regId)) shouldBe Some(empInfo)
    }
    "return None" in new Setup {
      when(mockRegistrationRepository.retrieveEmploymentInfo(eqTo(regId))(any()))
        .thenReturn(Future.successful(None))

      await(service.getEmploymentInfo(regId)) shouldBe None
    }
    "return a MissingRegDocument if the document is missing in repository" in new Setup {
      when(mockRegistrationRepository.retrieveEmploymentInfo(eqTo(regId))(any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      a[MissingRegDocument] shouldBe thrownBy(await(service.getEmploymentInfo(regId)))
    }
    "return a RetrieveFailed if the repository returns an error" in new Setup {
      when(mockRegistrationRepository.retrieveEmploymentInfo(eqTo(regId))(any()))
        .thenReturn(Future.failed(new RetrieveFailed(regId)))

      a[RetrieveFailed] shouldBe thrownBy(await(service.getEmploymentInfo(regId)))
    }
    "return an UpdateFailed if the repository returns an error to delete old model" in new Setup {
      when(mockRegistrationRepository.retrieveEmploymentInfo(eqTo(regId))(any()))
        .thenReturn(Future.failed(new UpdateFailed(regId, "test")))

      a[UpdateFailed] shouldBe thrownBy(await(service.getEmploymentInfo(regId)))
    }
  }

  "Calling upsertEmploymentInfo" should {
    val empInfo = EmploymentInfo(Employing.alreadyEmploying, LocalDate.of(2018,4,9), true, true, Some(true))
    "upsert successfully and also set the paye status to draft" in new Setup {
     when(mockRegistrationRepository.upsertEmploymentInfo(eqTo(regId),any())(any()))
        .thenReturn(Future.successful(empInfo))
      when(mockRegistrationRepository.updateRegistrationStatus(eqTo(regId), any[PAYEStatus.Value]())(any()))
        .thenReturn(Future.successful(PAYEStatus.draft))

      val res = await(service.upsertEmploymentInfo(regId,empInfo))
      res shouldBe empInfo

      val captor:ArgumentCaptor[PAYEStatus.Value] = ArgumentCaptor.forClass(classOf[PAYEStatus.Value])
      verify(mockRegistrationRepository, times(1)).updateRegistrationStatus(eqTo(regId), captor.capture())(any())
      captor.getValue shouldBe PAYEStatus.draft

    }
    "upsert successfully and also set the paye status to invalid" in new Setup {
      val empInfoForInvalid = empInfo.copy(employees = Employing.notEmploying,LocalDate.of(2018,1,1),false,false,Some(true))
      when(mockRegistrationRepository.upsertEmploymentInfo(eqTo(regId),any())(any()))
        .thenReturn(Future.successful(empInfoForInvalid))
      when(mockRegistrationRepository.updateRegistrationStatus(eqTo(regId), any[PAYEStatus.Value]())(any()))
        .thenReturn(Future.successful(PAYEStatus.invalid))

      val res = await(service.upsertEmploymentInfo(regId, empInfoForInvalid))
      res shouldBe empInfoForInvalid

      val captor: ArgumentCaptor[PAYEStatus.Value]  = ArgumentCaptor.forClass(classOf[PAYEStatus.Value])
      verify(mockRegistrationRepository, times(1)).updateRegistrationStatus(eqTo(regId), captor.capture())(any())
      captor.getValue shouldBe PAYEStatus.invalid
    }
    "throw a MissingRegDocument if the repository throws a MissingRegDocument Exception" in new Setup {
      when(mockRegistrationRepository.upsertEmploymentInfo(eqTo(regId),any())(any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      intercept[MissingRegDocument](await(service.upsertEmploymentInfo(regId, empInfo)))
      verify(mockRegistrationRepository, times(0)).updateRegistrationStatus(any(),any())(any())
    }
  }

  "Calling getIncorporationDate" should {
    implicit val hc = HeaderCarrier(sessionId = Some(SessionId("session-123")))

    "return a None response when there is no incorporation date" in new Setup {
      when(mockRegistrationRepository.retrieveTransactionId(eqTo(regId))(any()))
        .thenReturn(Future.successful("test-txId"))

      when(mockIncorporationInformationConnector.getIncorporationDate(eqTo("test-txId"))(any()))
        .thenReturn(Future.successful(None))

      val actual = await(service.getIncorporationDate(regId))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveTransactionId(eqTo(regId))(any()))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getIncorporationDate(regId)) }
    }

    "return an incorporation date" in new Setup {
      when(mockRegistrationRepository.retrieveTransactionId(eqTo(regId))(any()))
        .thenReturn(Future.successful("test-txId"))

      when(mockIncorporationInformationConnector.getIncorporationDate(eqTo("test-txId"))(any()))
        .thenReturn(Future.successful(Some(LocalDate.of(2017, 6, 4))))

      val actual = await(service.getIncorporationDate(regId))
      actual shouldBe Some(LocalDate.of(2017, 6, 4))
    }
  }

  "Calling getDirectors" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveDirectors(eqTo(regId))(any()))
        .thenReturn(Future.successful(Seq.empty))

      val actual = await(service.getDirectors(regId))
      actual shouldBe Seq.empty
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveDirectors(eqTo(regId))(any()))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getDirectors(regId)) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveDirectors(eqTo(regId))(any()))
        .thenReturn(Future.successful(validDirectors))

      val actual = await(service.getDirectors(regId))
      actual shouldBe validRegistration.directors
    }
  }

  "Calling upsertDirectors" should {

    "throw a MissingRegDocument exception when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertDirectors(eqTo(regId), any[Seq[Director]]())(any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      intercept[MissingRegDocument] { await(service.upsertDirectors(regId, validDirectors)) }
    }

    "throw a RegistrationFormatException exception when there are no NINOs defined in the directors list" in new Setup {
      val noNinosDirectors = validDirectors.map(_.copy(nino = None))
      intercept[RegistrationFormatException] { await(service.upsertDirectors(regId, noNinosDirectors)) }
    }

    "return a list of directors when the director details are successfully updated" in new Setup {
      when(mockRegistrationRepository.upsertDirectors(eqTo(regId), any[Seq[Director]]())(any()))
        .thenReturn(Future.successful(validDirectors))

      val actual = await(service.upsertDirectors(regId, validDirectors))
      actual shouldBe validDirectors
    }
  }

  "Calling getSICCodes" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveSICCodes(eqTo(regId))(any()))
        .thenReturn(Future.successful(Seq.empty))

      val actual = await(service.getSICCodes(regId))
      actual shouldBe Seq.empty
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveSICCodes(eqTo(regId))(any()))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getSICCodes(regId)) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveSICCodes(eqTo(regId))(any()))
        .thenReturn(Future.successful(validSICCodes))

      val actual = await(service.getSICCodes(regId))
      actual shouldBe validRegistration.sicCodes
    }
  }

  "Calling upsertSICCodes" should {

    "return a DBNotFound response when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertSICCodes(eqTo(regId), any[Seq[SICCode]]())(any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      intercept[MissingRegDocument] { await(service.upsertSICCodes(regId, validSICCodes)) }
    }

    "return a DBSuccess response when the company details are successfully updated" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.upsertSICCodes(eqTo(regId), any[Seq[SICCode]]())(any()))
        .thenReturn(Future.successful(validSICCodes))

      val actual = await(service.upsertSICCodes(regId, validSICCodes))
      actual shouldBe validSICCodes
    }
  }

  "Calling getPAYEContact" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrievePAYEContact(eqTo(regId))(any()))
        .thenReturn(Future.successful(None))

      val actual = await(service.getPAYEContact(regId))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrievePAYEContact(eqTo(regId))(any()))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getPAYEContact(regId)) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrievePAYEContact(eqTo(regId))(any()))
        .thenReturn(Future.successful(Some(validPAYEContact)))

      val actual = await(service.getPAYEContact(regId))
      actual shouldBe validRegistration.payeContact
    }
  }

  "Calling upsertPAYEContact" should {

    "throw a missingRegDocument exception when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.upsertPAYEContact(eqTo(regId), any[PAYEContact]())(any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      intercept[MissingRegDocument] { await(service.upsertPAYEContact(regId, validPAYEContact)) }
    }

    "throw a RegistrationFormatException when the PAYE Contact has no contact method defined" in new Setup {
      val invalidPAYEContact = validPAYEContact.copy(
        contactDetails = PAYEContactDetails(
          name = "Name", digitalContactDetails = DigitalContactDetails(None, None, None)
        )
      )
      intercept[RegistrationFormatException] { await(service.upsertPAYEContact(regId, invalidPAYEContact)) }
    }

    "return a PAYE Contact model when the paye contact are successfully updated" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.upsertPAYEContact(eqTo(regId), any[PAYEContact]())(any()))
        .thenReturn(Future.successful(validPAYEContact))

      val actual = await(service.upsertPAYEContact(regId, validPAYEContact))
      actual shouldBe validPAYEContact
    }
  }

  "Calling getCompletionCapacity" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveCompletionCapacity(eqTo(regId))(any()))
        .thenReturn(Future.successful(None))

      val actual = await(service.getCompletionCapacity(regId))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveCompletionCapacity(eqTo(regId))(any()))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getCompletionCapacity(regId)) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveCompletionCapacity(eqTo(regId))(any()))
        .thenReturn(Future.successful(Some("Director")))

      val actual = await(service.getCompletionCapacity(regId))
      actual shouldBe validRegistration.completionCapacity
    }
  }

  "Calling upsertCompletionCapacity" should {
    implicit val hc = HeaderCarrier(sessionId = Some(SessionId("session-123")))

    "throw a MissingRegDocument when there is no registration in mongo with the user's ID" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(eqTo(regId))(any()))
        .thenReturn(Future.failed(new MissingRegDocument(regId)))

      intercept[MissingRegDocument] { await(service.upsertCompletionCapacity(regId, "Agent")) }
    }

    "throw a MissingRegDocument when there is no registration in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(eqTo(regId))(any()))
        .thenReturn(Future.successful(None))

      intercept[MissingRegDocument] { await(service.upsertCompletionCapacity(regId, "Agent")) }
    }

    "return a DBSuccess response when the paye contact are successfully updated" in new Setup {
      val exception = new RuntimeException("tst message")

      when(mockRegistrationRepository.retrieveRegistration(eqTo(regId))(any()))
        .thenReturn(Future.successful(Some(validRegistration)))

      when(mockAuditService.auditCompletionCapacity(any(), any(), any())(any()))
        .thenReturn(Future.successful(AuditResult.Success))

      when(mockRegistrationRepository.upsertCompletionCapacity(eqTo(regId), any())(any()))
        .thenReturn(Future.successful("Agent"))

      val actual = await(service.upsertCompletionCapacity(regId, List.fill(100)('a').mkString))
      actual shouldBe "Agent"
    }
  }

  "Calling getAcknowledgementReference" should {

    "return a None response when there is no registration in mongo for the reg ID" in new Setup {
      when(mockRegistrationRepository.retrieveAcknowledgementReference(eqTo(regId))(any()))
        .thenReturn(Future.successful(None))

      val actual = await(service.getAcknowledgementReference(regId))
      actual shouldBe None
    }

    "return a failed future with exception when the database errors" in new Setup {
      val exception = new RuntimeException("tst message")
      when(mockRegistrationRepository.retrieveAcknowledgementReference(eqTo(regId))(any()))
        .thenReturn(Future.failed(exception))

      intercept[RuntimeException] { await(service.getAcknowledgementReference(regId)) }
    }

    "return a registration there is one matching the reg ID in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveAcknowledgementReference(eqTo(regId))(any()))
        .thenReturn(Future.successful(Some("tstBRPY")))

      val actual = await(service.getAcknowledgementReference(regId))
      actual shouldBe Some("tstBRPY")
    }
  }

  "deletePAYERegistration" should {
    "return true" when {
      "the document has been deleted as the users document was rejected" in new Setup {
        when(mockRegistrationRepository.retrieveRegistration(any())(any()))
          .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.rejected))))

        when(mockRegistrationRepository.deleteRegistration(any())(any()))
          .thenReturn(Future.successful(true))

        val result = await(service.deletePAYERegistration(validRegistration.registrationID, PAYEStatus.rejected))
        result shouldBe true
      }
    }

    "throw a StatusNotRejectedException" when {
      "the document status is not rejected" in new Setup {
        when(mockRegistrationRepository.retrieveRegistration(any())(any()))
          .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.held))))

        intercept[UnmatchedStatusException](await(service.deletePAYERegistration(validRegistration.registrationID)))
      }
    }

    "throw a MissingRegDocument" when {
      "the document was not found against the regId" in new Setup {
        when(mockRegistrationRepository.retrieveRegistration(any())(any()))
          .thenReturn(Future.successful(None))

        intercept[MissingRegDocument](await(service.deletePAYERegistration(validRegistration.registrationID)))
      }
    }
  }

  "getRegistrationId" should {
    "return the regId" in new Setup {
      when(service.getRegistrationId(any())(any()))
        .thenReturn(Future("testRegId"))

      await(service.getRegistrationId("txId")) shouldBe "testRegId"
    }

    "throw a MissingRegDocException" in new Setup {
      when(service.getRegistrationId(any())(any()))
        .thenReturn(Future.failed(new MissingRegDocument("")))

      intercept[MissingRegDocument](await(service.getRegistrationId("txId")))
    }

    "throw an IllegalStateException" in new Setup {
      when(service.getRegistrationId(any())(any()))
        .thenReturn(Future.failed(new IllegalStateException()))

      intercept[IllegalStateException](await(service.getRegistrationId("txId")))
    }
  }
}
