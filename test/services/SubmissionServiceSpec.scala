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

import java.time.{LocalDate, LocalDateTime, ZoneOffset, ZonedDateTime}

import common.exceptions.DBExceptions.MissingRegDocument
import common.exceptions.RegistrationExceptions._
import common.exceptions.SubmissionExceptions._
import connectors._
import enums.{Employing, IncorporationStatus, PAYEStatus}
import fixtures.SubmissionFixture
import models._
import models.submission._
import helpers.PAYERegSpec
import models.external.BusinessProfile
import models.incorporation.IncorpStatusUpdate
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.logging.SessionId

class SubmissionServiceSpec extends PAYERegSpec {

  val mockDESConnector = mock[DESConnector]
  val mockIIConnector = mock[IncorporationInformationConnector]
  val mockAuditService = mock[AuditService]
  val mockBusinessRegistrationConnector = mock[BusinessRegistrationConnector]
  val mockCompanyRegistrationConnector = mock[CompanyRegistrationConnector]
  val mockRegistrationService = mock[RegistrationService]

  implicit val hc = HeaderCarrier(sessionId = Some(SessionId("session-123")))
  implicit val req = FakeRequest("GET", "/test-path")

  class Setup {
    val service = new SubmissionSrv{
      override val sequenceRepository = mockSequenceRepository
      override val registrationRepository = mockRegistrationRepository
      override val desConnector = mockDESConnector
      override val incorporationInformationConnector = mockIIConnector
      override val authConnector = mockAuthConnector
      override val auditService = mockAuditService
      override val businessRegistrationConnector = mockBusinessRegistrationConnector
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val registrationService = mockRegistrationService
    }
  }

  override def beforeEach() {
    reset(mockRegistrationRepository)
    reset(mockSequenceRepository)
    reset(mockDESConnector)
    reset(mockAuthConnector)
    reset(mockRegistrationService)
  }

  val lastUpdate = "2017-05-09T07:58:35Z"
  val zDtNow = ZonedDateTime.of(LocalDateTime.of(1,1,1,1,1),ZoneOffset.UTC)

  val validCompanyDetails = CompanyDetails(
    companyName = "Test Company Name",
    tradingName = Some("Test Trading Name"),
    Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), Some("UK"), Some("roAuditRef")),
    Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK")),
    DigitalContactDetails(Some("test@email.com"), Some("012345"), Some("543210"))
  )

  val validCompanyDetailsWithCRN = CompanyDetails(
    companyName = "Test Company Name",
    tradingName = Some("Test Trading Name"),
    Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), Some("UK")),
    Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK")),
    DigitalContactDetails(Some("test@email.com"), Some("012345"), Some("543210"))
  )

  val validEmployment = EmploymentInfo(
    employees = Employing.alreadyEmploying,
    firstPaymentDate = LocalDate.of(2016, 12, 20),
    construction = true,
    subcontractors = true,
    companyPension = Some(true)
  )

  val validDirectors = Seq(
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

  val validSICCodes = Seq(
    SICCode(code = None, description = Some("consulting"))
  )

  val validPAYEContact = PAYEContact(
    contactDetails = PAYEContactDetails(
      name = "Toto Tata",
      digitalContactDetails = DigitalContactDetails(
        Some("test@email.com"),
        Some("012345"),
        Some("543210")
      )
    ),
    correspondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK"))
  )

  val validRegistration = PAYERegistration(
    registrationID = "regID",
    transactionID = "NNASD9789F",
    internalID = "internalID",
    eligibility = Some(Eligibility(
      companyEligibility = false,
      directorEligibility = false
    )),
    registrationConfirmation = Some(EmpRefNotification(
      empRef = Some("testEmpRef"),
      timestamp = "2017-01-01T12:00:00Z",
      status = "testStatus"
    )),
    status = PAYEStatus.draft,
    acknowledgementReference = Some("ackRef"),
    crn = None,
    formCreationTimestamp = "2017-05-03T12:51:42Z",
    companyDetails = Some(validCompanyDetails),
    completionCapacity = Some("director"),
    directors = validDirectors,
    payeContact = Some(validPAYEContact),
    sicCodes = validSICCodes,
    lastUpdate = lastUpdate,
    partialSubmissionTimestamp = None,
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(zDtNow),
    employmentInfo = Some(validEmployment)
  )

  val validRegistrationAfterPartialSubmission = PAYERegistration(
    registrationID = "regID",
    transactionID = "NNASD9789F",
    internalID = "internalID",
    eligibility = None,
    registrationConfirmation = None,
    status = PAYEStatus.held,
    acknowledgementReference = Some("ackRef"),
    crn = None,
    formCreationTimestamp = "2017-05-03T12:51:42Z",
    companyDetails = Some(validCompanyDetailsWithCRN),
    completionCapacity = None,
    directors = Seq.empty,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate = lastUpdate,
    partialSubmissionTimestamp = Some(lastUpdate),
    fullSubmissionTimestamp = None,
    acknowledgedTimestamp = None,
    lastAction = Some(zDtNow),
    employmentInfo = None
  )

  val validRegistrationAfterTopUpSubmission = PAYERegistration(
    registrationID = "regID",
    transactionID = "NNASD9789F",
    internalID = "internalID",
    eligibility = None,
    registrationConfirmation = Some(EmpRefNotification(
      empRef = Some("testEmpRef"),
      timestamp = "2017-01-01T12:00:00Z",
      status = "testStatus"
    )),
    status = PAYEStatus.submitted,
    acknowledgementReference = Some("ackRef"),
    crn = Some("OC123456"),
    formCreationTimestamp = "2017-05-03T12:51:42Z",
    companyDetails = None,
    completionCapacity = None,
    directors = Seq.empty,
    payeContact = None,
    sicCodes = Seq.empty,
    lastUpdate = lastUpdate,
    partialSubmissionTimestamp = Some(lastUpdate),
    fullSubmissionTimestamp = Some(lastUpdate),
    acknowledgedTimestamp = None,
    lastAction = Some(zDtNow),
    employmentInfo = None
  )

  val validDESCompletionCapacity = DESCompletionCapacity(
    capacity = "Director",
    otherCapacity = None
  )

  val validDESMetaData = DESMetaData(
    sessionId = "session-123",
    credId = "cred-123",
    language = "en",
    submissionTs = "2017-05-03T12:51:42Z",
    completionCapacity = validDESCompletionCapacity
  )

  val validDESLimitedCompanyWithoutCRN = DESLimitedCompany(
    companyUTR = None,
    companiesHouseCompanyName = "Test Company Name",
    nameOfBusiness = Some("Test Trading Name"),
    businessAddress = validCompanyDetails.ppobAddress,
    businessContactDetails = validCompanyDetails.businessContactDetails,
    natureOfBusiness = "consulting",
    crn = None,
    directors = validDirectors,
    registeredOfficeAddress = validCompanyDetails.roAddress,
    operatingOccPensionScheme = validEmployment.companyPension
  )

  val validDESEmployingPeople = DESEmployingPeople(
    dateOfFirstEXBForEmployees = LocalDate.of(2016, 12, 20),
    numberOfEmployeesExpectedThisYear = "1",
    engageSubcontractors = true,
    correspondenceName = "Toto Tata",
    correspondenceContactDetails = DigitalContactDetails(
      Some("test@email.com"),
      Some("012345"),
      Some("543210")
    ),
    payeCorrespondenceAddress = Address("19 St Walk", "Testley CA", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK"))
  )

  val validPartialDESSubmissionModel = DESSubmission(
    acknowledgementReference = "ackRef",
    metaData = validDESMetaData,
    limitedCompany = validDESLimitedCompanyWithoutCRN,
    employingPeople = validDESEmployingPeople
  )

  val incorpStatusUpdate = IncorpStatusUpdate(transactionId = "NNASD9789F",
                                              status = IncorporationStatus.accepted,
                                              crn = Some("123456"),
                                              incorporationDate = Some(LocalDate.of(2000, 12, 12)),
                                              description = None,
                                              timestamp = LocalDate.of(2017, 12, 21))

  val validTopUpDESSubmissionModel = TopUpDESSubmission(
    acknowledgementReference = "ackRef",
    status = IncorporationStatus.accepted,
    crn = Some("123456")
  )
  val validEmploymentInfo = EmploymentInfo(
    employees = Employing.alreadyEmploying,
    firstPaymentDate = LocalDate.of(2016, 12, 20),
    construction = true,
    subcontractors = true,
    companyPension = Some(false)
  )

  "RejectedIncorporationException" should {
    "return the message" when {
      "the exception is thrown" in {
        val result = intercept[RejectedIncorporationException](throw new RejectedIncorporationException("testMsg"))
        result.getMessage shouldBe "testMsg"
      }
    }
  }

  "payeReg2DESSubmission" should {
    "return a DESSubmission model" when {
      "a valid PAYE reg doc is passed to it" in new Setup {
        when(mockBusinessRegistrationConnector.retrieveCurrentProfile(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(BusinessProfile(validRegistration.registrationID, None, "en")))

        when(mockAuthConnector.authorise[Credentials](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Credentials("cred-123", "someProviderType")))

        val result = await(service.payeReg2DESSubmission(validRegistration, None, None))
        result shouldBe validPartialDESSubmissionModel
      }

      "a valid paye reg doc with a crn is passed to it" in new Setup {
        when(mockBusinessRegistrationConnector.retrieveCurrentProfile(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(BusinessProfile(validRegistration.registrationID, None, "en")))

        when(mockAuthConnector.authorise[Credentials](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Credentials("cred-123", "someProviderType")))

        val result = await(service.payeReg2DESSubmission(validRegistration, Some("OC123456"), None))
        result shouldBe validPartialDESSubmissionModel.copy(limitedCompany = validDESLimitedCompanyWithoutCRN.copy(crn = Some("OC123456")))
      }
    }

    "throw a CompanyDetailsNotDefinedException" when {
      "a paye reg doc is passed in that doesn't have a company details block" in new Setup {
        intercept[CompanyDetailsNotDefinedException](service.payeReg2DESSubmission(validRegistration.copy(companyDetails = None), None, None))
      }
    }

    "throw a AcknowledgementReferenceNotExistsException" when {
      "the paye reg doc is missing an ack ref" in new Setup {
        intercept[AcknowledgementReferenceNotExistsException](service.payeReg2DESSubmission(validRegistration.copy(acknowledgementReference = None), None, None))
      }
    }

    "throw a EmploymentDetailsNotDefinedException" when {
      "a paye reg doc is passed in that doesn't have an employment info block" in new Setup {
        intercept[EmploymentDetailsNotDefinedException](service.payeReg2DESSubmission(validRegistration.copy(employmentInfo = None), None, None))
      }
    }
  }

  "Calling assertOrGenerateAcknowledgementReference" should {
    "return an ack ref if there is one in the registration" in new Setup {
      when(mockRegistrationRepository.retrieveAcknowledgementReference(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some("tstAckRef")))

      await(service.assertOrGenerateAcknowledgementReference("regID")) shouldBe "tstAckRef"
    }

    "generate an ack ref if there isn't one in the registration" in new Setup {
      when(mockRegistrationRepository.retrieveAcknowledgementReference(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))
      when(mockSequenceRepository.getNext(ArgumentMatchers.contains("AcknowledgementID"))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(1234))
      when(mockRegistrationRepository.saveAcknowledgementReference(ArgumentMatchers.anyString(), ArgumentMatchers.contains("1234"))(ArgumentMatchers.any()))
      .thenReturn(Future.successful("BRPY00000001234"))

      await(service.assertOrGenerateAcknowledgementReference("regID")) shouldBe "BRPY00000001234"
    }
  }

  "Calling buildADesSubmission" should {
    "throw the correct exception when there is no registration in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      intercept[MissingRegDocument](await(service.buildADesSubmission("regId", Some(incorpStatusUpdate), None)))
    }

    "throw the correct exception when PAYE status is in an incorrect state" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.acknowledged))))

      intercept[RegistrationInvalidStatus](await(service.buildADesSubmission("regId", Some(incorpStatusUpdate), None)))
    }
  }

  "Calling buildTopUpDESSubmission" should {
    "throw the correct exception when there is no registration in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      intercept[MissingRegDocument](await(service.buildTopUpDESSubmission("regId", incorpStatusUpdate)))
    }

    "throw the correct exception when the registration is not yet submitted" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.draft))))

      intercept[RegistrationInvalidStatus](await(service.buildTopUpDESSubmission("regId", incorpStatusUpdate)))
    }

    "throw the correct exception when the registration is already submitted" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.submitted))))

      intercept[ErrorRegistrationException](await(service.buildTopUpDESSubmission("regId", incorpStatusUpdate)))
    }
  }

  "Building DES Limited Company" should {
    "throw the correct exception when Directors list is empty" in new Setup {
      intercept[DirectorsNotCompletedException](service.buildDESLimitedCompany(validCompanyDetails, validSICCodes, None, Seq.empty, validEmploymentInfo, None))
    }
    "throw the correct exception for SIC Code when missing)" in new Setup {
      intercept[SICCodeNotDefinedException](service.buildDESLimitedCompany(validCompanyDetails, Seq.empty, None, validDirectors, validEmploymentInfo, None))
    }
  }

  "Building DES Employing People" should {

    "throw the correct exception for PAYE Contact when missing" in new Setup {
      intercept[PAYEContactNotDefinedException](service.buildDESEmployingPeople("regId", validEmployment, None))
    }

    "return the correct DES Employing People model" when {
      "employees is willEmployThisYear" in new Setup {
        service.buildDESEmployingPeople("regId", validEmploymentInfo.copy(employees = Employing.willEmployThisYear), Some(validPAYEContact)) shouldBe validDESEmployingPeople
      }

      "employees is willEmployNextYear" in new Setup {
        service.buildDESEmployingPeople("regId", validEmploymentInfo.copy(employees = Employing.willEmployNextYear), Some(validPAYEContact)) shouldBe validDESEmployingPeople
      }

      "employees is notEmploying" in new Setup {
        service.buildDESEmployingPeople("regId", validEmploymentInfo.copy(employees = Employing.notEmploying), Some(validPAYEContact)) shouldBe validDESEmployingPeople.copy(numberOfEmployeesExpectedThisYear = "0")
      }
    }
  }

  "Building DES Completion Capacity" should {
    "succeed for agent" in new Setup {
      DESCompletionCapacity.buildDESCompletionCapacity(Some("agent")) shouldBe DESCompletionCapacity("Agent", None)
    }
    "succeed for secretary" in new Setup {
      DESCompletionCapacity.buildDESCompletionCapacity(Some("company secretary")) shouldBe DESCompletionCapacity("Company secretary", None)
    }
    "succeed for 'DiReCTOR  '" in new Setup {
      DESCompletionCapacity.buildDESCompletionCapacity(Some("DiReCTOR  ")) shouldBe DESCompletionCapacity("Director", None)
    }
    "succeed for 'high priestess'" in new Setup {
      DESCompletionCapacity.buildDESCompletionCapacity(Some("high priestess")) shouldBe DESCompletionCapacity("Other", Some("high priestess"))
    }
    "throw the correct exception for Completion Capacity when missing" in new Setup {
      intercept[CompletionCapacityNotDefinedException](DESCompletionCapacity.buildDESCompletionCapacity(None))
    }
  }

  "Building DES Nature Of Business" should {
    "succeed for agent" in new Setup {
      service.buildNatureOfBusiness(Seq(SICCode(None, Some("consulting")))) shouldBe "consulting"
    }
    "throw the correct exception for SIC Code when missing" in new Setup {
      intercept[SICCodeNotDefinedException](service.buildNatureOfBusiness(Seq.empty))
    }
    "throw the correct exception for SIC Code when description is missing" in new Setup {
      intercept[SICCodeNotDefinedException](service.buildNatureOfBusiness(Seq(SICCode(None, None))))
    }
  }

  "payeReg2TopUpDESSubmission" should {
    "throw the correct error when acknowledgement reference is not present" in new Setup{
      intercept[AcknowledgementReferenceNotExistsException](service.payeReg2TopUpDESSubmission(validRegistration.copy(acknowledgementReference = None), incorpStatusUpdate))
    }
    "build a Top Up" in new Setup{
      service.payeReg2TopUpDESSubmission(validRegistrationAfterPartialSubmission, incorpStatusUpdate) shouldBe validTopUpDESSubmissionModel
    }
  }

  "Calling submitToDes" should {
    "throw an Exception when the incorporation is rejected" in new Setup {
      when(mockRegistrationRepository.retrieveAcknowledgementReference(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))
      when(mockSequenceRepository.getNext(ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(1))
      when(mockRegistrationRepository.saveAcknowledgementReference(ArgumentMatchers.any(),ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful("foo"))
      when(mockRegistrationRepository.retrieveTransactionId(ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful("bar"))
      when(mockIIConnector.getIncorporationUpdate(ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(incorpStatusUpdate.copy(status = IncorporationStatus.rejected))))

      intercept[RejectedIncorporationException](await(service.submitToDes("regID")))
      verify(mockRegistrationService,times(1)).deletePAYERegistration(ArgumentMatchers.any(),ArgumentMatchers.any())(ArgumentMatchers.any())
    }
    "return an ackref" when {
      "there is no incorporation" in new Setup {
        when(mockRegistrationRepository.retrieveAcknowledgementReference(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))
        when(mockSequenceRepository.getNext(ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(1))
        when(mockRegistrationRepository.saveAcknowledgementReference(ArgumentMatchers.any(),ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful("foo"))
        when(mockRegistrationRepository.retrieveTransactionId(ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful("bar"))
        when(mockIIConnector.getIncorporationUpdate(ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))
        when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(validRegistration)))
        when(mockAuthConnector.authorise[Credentials](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Credentials("some-provider-id", "some-provider-type")))
        when(mockDESConnector.submitToDES(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(200)))
        when(mockAuditService.auditDESSubmission(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Success))
        when(mockRegistrationRepository.updateRegistrationStatus(ArgumentMatchers.anyString(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(PAYEStatus.held))

        await(service.submitToDes("regID")) shouldBe "BRPY00000000001"
      }

      "the incorporation is accepted" in new Setup {
        val okResponse = new HttpResponse {
          override def status: Int = OK

          override def json: JsValue = Json.parse(
            """
              |{
              | "acknowledgementReferences" : {
              |   "ctUtr" : "testCtUtr"
              | }
              |}
            """.stripMargin
          )
        }

        when(mockRegistrationRepository.retrieveAcknowledgementReference(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))
        when(mockSequenceRepository.getNext(ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(1))
        when(mockRegistrationRepository.saveAcknowledgementReference(ArgumentMatchers.any(),ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful("foo"))
        when(mockRegistrationRepository.retrieveTransactionId(ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful("bar"))
        when(mockIIConnector.getIncorporationUpdate(ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(incorpStatusUpdate)))
        when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(validRegistration)))
        when(mockAuthConnector.authorise[Credentials](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Credentials("some-provider-id", "some-provider-type")))
        when(mockCompanyRegistrationConnector.fetchCompanyRegistrationDocument(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(okResponse))
        when(mockDESConnector.submitToDES(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(200)))
        when(mockAuditService.auditDESSubmission(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Success))
        when(mockRegistrationRepository.updateRegistrationStatus(ArgumentMatchers.anyString(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(PAYEStatus.submitted))

        await(service.submitToDes("regID")) shouldBe "BRPY00000000001"
      }
    }
  }

  "Calling submitTopUpToDES" should {
    "return the correct PAYE status" when {
      "incorporation is accepted" in new Setup {
        val okResponse = new HttpResponse {
          override def status: Int = OK

          override def json: JsValue = Json.parse(
            """
              |{
              | "acknowledgementReferences" : {
              |   "ctUtr" : "testCtUtr"
              | }
              |}
            """.stripMargin
          )
        }

        when(mockCompanyRegistrationConnector.fetchCompanyRegistrationDocument(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(okResponse))

        when(mockRegistrationRepository.retrieveAcknowledgementReference(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some("ackRef")))

        when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(validRegistrationAfterPartialSubmission)))

        when(mockDESConnector.submitTopUpToDES(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(200)))

        when(mockAuditService.auditDESTopUp(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Success))

        when(mockRegistrationRepository.updateRegistrationStatus(ArgumentMatchers.anyString(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(PAYEStatus.submitted))

        when(mockRegistrationRepository.cleardownRegistration(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(validRegistrationAfterTopUpSubmission))

        await(service.submitTopUpToDES("regID", incorpStatusUpdate)) shouldBe PAYEStatus.submitted
      }

      "incorporation is rejected" in new Setup {
        val okResponse = new HttpResponse {
          override def status: Int = OK

          override def json: JsValue = Json.parse(
            """
              |{
              | "acknowledgementReferences" : {}
              |}
            """.stripMargin
          )
        }

        when(mockCompanyRegistrationConnector.fetchCompanyRegistrationDocument(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(okResponse))

        when(mockRegistrationRepository.retrieveAcknowledgementReference(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some("ackRef")))

        when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.anyString())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(validRegistrationAfterPartialSubmission)))

        when(mockDESConnector.submitTopUpToDES(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(200)))

        when(mockAuditService.auditDESTopUp(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Success))

        when(mockRegistrationService.deletePAYERegistration(ArgumentMatchers.anyString(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(true))

        await(service.submitTopUpToDES("regID", incorpStatusUpdate.copy(status = IncorporationStatus.rejected))) shouldBe PAYEStatus.cancelled
      }
    }
  }

  "Calling retrieveCredId" should {
    "return the correct exception when credential ID is missing" in new Setup {
      when(mockAuthConnector.authorise(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.failed(new Exception("Can't get providerId from credentials")))

      a[service.FailedToGetCredId] shouldBe thrownBy(await(service.retrieveCredId))
    }
  }

  "Calling retrieveLanguage" should {
    "return the correct exception for language" in new Setup {
      when(mockBusinessRegistrationConnector.retrieveCurrentProfile(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessProfile(validRegistration.registrationID, None, "en")))

      a[service.FailedToGetLanguage] shouldBe thrownBy(await(service.retrieveLanguage("testRegId")))
    }
  }

  "Calling retrieveSessionID" should {
    "return the correct exception when session ID is missing" in new Setup {
      intercept[service.SessionIDNotExists](service.retrieveSessionID(HeaderCarrier()))
    }
  }

  "fetchCtUtr" should {
    "return some ctutr" when {
      "the ctutr is part of the CR doc" in new Setup {
        val testJson = Json.parse(
          """
            |{
            | "acknowledgementReferences": {
            |   "ctUtr" : "testCtUtr"
            | }
            |}
          """.stripMargin
        )

        val okResponse = new HttpResponse {
          override def status: Int = OK
          override def json: JsValue = testJson
        }

        when(mockCompanyRegistrationConnector.fetchCompanyRegistrationDocument(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(okResponse))

        val result = await(service.fetchCtUtr("testRegId", Some(incorpStatusUpdate)))
        result shouldBe Some("testCtUtr")
      }
    }

    "return none" when {
      "the ctutr isn't part of the CR doc" in new Setup {
        val testJson = Json.parse(
          """
            |{
            | "acknowledgementReferences": {
            |   "invalidKey" : "testCtUtr"
            | }
            |}
          """.stripMargin
        )

        val okResponse = new HttpResponse {
          override def status: Int = OK
          override def json: JsValue = testJson
        }

        when(mockCompanyRegistrationConnector.fetchCompanyRegistrationDocument(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(okResponse))

        val result = await(service.fetchCtUtr("testRegId", Some(incorpStatusUpdate)))
        result shouldBe None
      }
    }
  }
}
