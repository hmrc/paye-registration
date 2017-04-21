/*
 * Copyright 2017 HM Revenue & Customs
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

import common.exceptions.DBExceptions.MissingRegDocument
import common.exceptions.RegistrationExceptions._
import common.exceptions.SubmissionExceptions._
import connectors.{DESConnector, IncorporationInformationConnector}
import enums.PAYEStatus
import models._
import models.submission._
import helpers.PAYERegSpec
import models.incorporation.IncorpStatusUpdate
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

class SubmissionServiceSpec extends PAYERegSpec {

  val mockDESConnector = mock[DESConnector]
  val mockIIConnector = mock[IncorporationInformationConnector]
  val mockAuditConnector = mock[AuditConnector]

  implicit val hc = HeaderCarrier()
  implicit val req = FakeRequest("GET", "/test-path")

  class Setup {
    val service = new SubmissionSrv{
      override val sequenceRepository = mockSequenceRepository
      override val registrationRepository = mockRegistrationRepository
      override val desConnector = mockDESConnector
      override val incorporationInformationConnector = mockIIConnector
      override val authConnector = mockAuthConnector
      override val auditConnector = mockAuditConnector
    }
  }

  override def beforeEach() {
    reset(mockRegistrationRepository)
    reset(mockSequenceRepository)
    reset(mockDESConnector)
  }


  val validCompanyDetails = CompanyDetails(
    companyName = "Test Company Name",
    tradingName = Some("Test Trading Name"),
    Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), Some("UK")),
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

  val validDESCompanyDetails = DESCompanyDetails(
    companyName = "Test Company Name",
    tradingName = Some("Test Trading Name"),
    ppob = Address("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE4 1ST"), Some("UK")),
    regAddress = Address("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), Some("UK"))
  )

  val validDESBusinessContact = DESBusinessContact(
    email = Some("test@email.com"),
    tel = Some("012345"),
    mobile = Some("543210")
  )

  val validEmployment = Employment(
    employees = true,
    companyPension = Some(true),
    subcontractors = true,
    firstPaymentDate = LocalDate.of(2016, 12, 20)
  )

  val validDESEmployment = DESEmployment(
    employees = true,
    ocpn = Some(true),
    cis = true,
    firstPaymentDate = LocalDate.of(2016, 12, 20)
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

  val validDESDirectors = Seq(
    DESDirector(
      forename = Some("Thierry"),
      otherForenames = Some("Dominique"),
      surname = Some("Henry"),
      title = Some("Sir"),
      nino = Some("SR123456C")
    ),
    DESDirector(
      forename = Some("David"),
      otherForenames = Some("Jesus"),
      surname = Some("Trezeguet"),
      title = Some("Mr"),
      nino = Some("SR000009C")
    )
  )

  val validSICCodes = Seq(
    SICCode(code = None, description = Some("consulting"))
  )

  val validDESSICCodes = Seq(
    DESSICCode(code = None, description = Some("consulting"))
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

  val validDESPAYEContact = DESPAYEContact(
    name = "Toto Tata",
    email = Some("test@email.com"),
    tel = Some("012345"),
    mobile = Some("543210"),
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
    formCreationTimestamp = "the year of the rooster",
    companyDetails = Some(validCompanyDetails),
    completionCapacity = Some("director"),
    directors = validDirectors,
    payeContact = Some(validPAYEContact),
    employment = Some(validEmployment),
    sicCodes = validSICCodes
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
    formCreationTimestamp = "the year of the rooster",
    companyDetails = Some(validCompanyDetailsWithCRN),
    completionCapacity = None,
    directors = Seq.empty,
    payeContact = None,
    employment = None,
    sicCodes = Seq.empty
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
    formCreationTimestamp = "the year of the rooster",
    companyDetails = None,
    completionCapacity = None,
    directors = Seq.empty,
    payeContact = None,
    employment = None,
    sicCodes = Seq.empty
  )

  val validPartialDESSubmissionModel = DESSubmissionModel(
    acknowledgementReference = "ackRef",
    crn = None,
    company = validDESCompanyDetails,
    directors = validDESDirectors,
    payeContact = validDESPAYEContact,
    businessContact = validDESBusinessContact,
    sicCodes = validDESSICCodes,
    employment = validDESEmployment,
    completionCapacity = DESCompletionCapacity("director", None)
  )

  val incorpStatusUpdate = IncorpStatusUpdate(transactionId = "NNASD9789F",
                                              status = "accepted",
                                              crn = Some("123456"),
                                              incorporationDate = Some(LocalDate.of(2000, 12, 12)),
                                              description = None,
                                              timestamp = "2017-12-21T10:13:09.429Z")

  val validTopUpDESSubmissionModel = TopUpDESSubmission(
    acknowledgementReference = "ackRef",
    status = "accepted",
    crn = Some("123456")
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
        val result = service.payeReg2DESSubmission(validRegistration, None)
        result shouldBe validPartialDESSubmissionModel
      }

      "a valid paye reg doc with a crn is passed to it" in new Setup {
        val result = service.payeReg2DESSubmission(validRegistration, Some("OC123456"))
        result shouldBe validPartialDESSubmissionModel.copy(crn = Some("OC123456"))
      }
    }

    "throw a CompanyDetailsNotDefinedException" when {
      "a paye reg doc is passed in that doesn't have a company details block" in new Setup {
        intercept[CompanyDetailsNotDefinedException](service.payeReg2DESSubmission(validRegistration.copy(companyDetails = None), None))
      }
    }

    "throw a AcknowledgementReferenceNotExistsException" when {
      "the paye reg doc is missing an ack ref" in new Setup {
        intercept[AcknowledgementReferenceNotExistsException](service.payeReg2DESSubmission(validRegistration.copy(acknowledgementReference = None), None))
      }
    }
  }

  "Calling assertOrGenerateAcknowledgementReference" should {
    "return an ack ref if there is one in the registration" in new Setup {
      when(mockRegistrationRepository.retrieveAcknowledgementReference(ArgumentMatchers.anyString()))
          .thenReturn(Future.successful(Some("tstAckRef")))

      await(service.assertOrGenerateAcknowledgementReference("regID")) shouldBe "tstAckRef"
    }

    "generate an ack ref if there isn't one in the registration" in new Setup {
      when(mockRegistrationRepository.retrieveAcknowledgementReference(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(None))
      when(mockSequenceRepository.getNext(ArgumentMatchers.contains("AcknowledgementID")))
        .thenReturn(Future.successful(1234))
      when(mockRegistrationRepository.saveAcknowledgementReference(ArgumentMatchers.anyString(), ArgumentMatchers.contains("1234")))
      .thenReturn(Future.successful("BRPY00000001234"))

      await(service.assertOrGenerateAcknowledgementReference("regID")) shouldBe "BRPY00000001234"
    }
  }

  "Calling buildTopUpDESSubmission" should {
    "throw the correct exception when there is no registration in mongo" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(None))

      intercept[MissingRegDocument](await(service.buildTopUpDESSubmission("regId", incorpStatusUpdate)))
    }

    "throw the correct exception when the registration is not yet submitted" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.draft))))

      intercept[InvalidRegistrationException](await(service.buildTopUpDESSubmission("regId", incorpStatusUpdate)))
    }

    "throw the correct exception when the registration is already submitted" in new Setup {
      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(Some(validRegistration.copy(status = PAYEStatus.submitted))))

      intercept[InvalidRegistrationException](await(service.buildTopUpDESSubmission("regId", incorpStatusUpdate)))
    }
  }

  "Calling build functions with undefined options" should {
    "throw the correct exception for PAYE Contact" in new Setup {
      intercept[PAYEContactNotDefinedException](service.buildDESPAYEContact(None))
    }
    "throw the correct exception for Employment Details" in new Setup {
      intercept[EmploymentDetailsNotDefinedException](service.buildDESEmploymentDetails(None))
    }
    "throw the correct exception for Completion Capacity" in new Setup {
      intercept[CompletionCapacityNotDefinedException](service.buildDESCompletionCapacity(None))
    }
  }

  "Building DES Completion Capacity" should {
    "succeed for agent" in new Setup {
      service.buildDESCompletionCapacity(Some("agent")) shouldBe DESCompletionCapacity("agent", None)
    }
    "succeed for 'DiReCTOR  '" in new Setup {
      service.buildDESCompletionCapacity(Some("DiReCTOR  ")) shouldBe DESCompletionCapacity("director", None)
    }
    "succeed for 'high priestess'" in new Setup {
      service.buildDESCompletionCapacity(Some("high priestess")) shouldBe DESCompletionCapacity("other", Some("high priestess"))
    }
  }

  "Building a Top Up DES Submission" should {
    "throw the correct error when acknowledgement reference is not present" in new Setup{
      intercept[AcknowledgementReferenceNotExistsException](service.payeReg2TopUpDESSubmission(validRegistration.copy(acknowledgementReference = None), incorpStatusUpdate))
    }
    "build a Top Up" in new Setup{
      service.payeReg2TopUpDESSubmission(validRegistrationAfterPartialSubmission, incorpStatusUpdate) shouldBe validTopUpDESSubmissionModel
    }
  }

  "Calling submitTopUpToDES" should {
    "return the acknowledgement reference" in new Setup {
      when(mockRegistrationRepository.retrieveAcknowledgementReference(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(Some("ackRef")))

      when(mockRegistrationRepository.retrieveRegistration(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(Some(validRegistrationAfterPartialSubmission)))

      when(mockDESConnector.submitToDES(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      when(mockRegistrationRepository.updateRegistrationStatus(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(PAYEStatus.submitted))

      when(mockRegistrationRepository.cleardownRegistration(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(validRegistrationAfterTopUpSubmission))

      await(service.submitTopUpToDES("regID", incorpStatusUpdate)) shouldBe PAYEStatus.submitted
    }
  }
}
