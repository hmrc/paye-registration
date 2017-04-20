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

import javax.inject.{Inject, Singleton}

import common.exceptions.DBExceptions.MissingRegDocument
import common.exceptions.RegistrationExceptions._
import common.exceptions.SubmissionExceptions._
import connectors.{DESConnect, DESConnector, IncorporationInformationConnect, IncorporationInformationConnector}
import enums.PAYEStatus
import models._
import models.incorporation.IncorpStatusUpdate
import models.submission._
import play.api.Logger
import repositories._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NoStackTrace

class RejectedIncorporationException(msg: String) extends NoStackTrace {
  override def getMessage: String = msg
}

@Singleton
class SubmissionService @Inject()(injSequenceMongoRepository: SequenceMongo,
                                  injRegistrationMongoRepository: RegistrationMongo,
                                  injDESConnector: DESConnector,
                                  injIncorprorationInformationConnector: IncorporationInformationConnector) extends SubmissionSrv {
  val sequenceRepository = injSequenceMongoRepository.store
  val registrationRepository = injRegistrationMongoRepository.store
  val desConnector = injDESConnector
  val incorporationInformationConnector = injIncorprorationInformationConnector
}

trait SubmissionSrv {

  val sequenceRepository: SequenceRepository
  val registrationRepository: RegistrationRepository
  val desConnector: DESConnect
  val incorporationInformationConnector: IncorporationInformationConnect

  private val REGIME = "paye"
  private val SUBSCRIBER = "SCRS"
  private val CALLBACK_URL = controllers.routes.RegistrationController.processIncorporationData().url

  def submitToDes(regId: String)(implicit hc: HeaderCarrier): Future[String] = {
    for {
      ackRef      <- assertOrGenerateAcknowledgementReference(regId)
      incStatus   <- checkIncorpStatus(regId)
      submission  <- buildADesSubmission(regId, incStatus)
      desResponse <- desConnector.submitToDES(submission)
      _           <- incStatus match {
        case Some(_) => processSuccessfulDESResponse(regId, PAYEStatus.submitted)
        case None    => processSuccessfulDESResponse(regId, PAYEStatus.held)
      }
    } yield ackRef
  }

  def submitTopUpToDES(regId: String, incorpStatusUpdate: IncorpStatusUpdate)(implicit hc: HeaderCarrier): Future[PAYEStatus.Value] = {
    for {
      desSubmission <- buildTopUpDESSubmission(regId, incorpStatusUpdate)
      _             <- desConnector.submitToDES(desSubmission)
      status        <- processSuccessfulDESResponse(regId, PAYEStatus.submitted)
    } yield status
  }

  def checkIncorpStatus(regId: String)(implicit hc: HeaderCarrier): Future[Option[IncorpStatusUpdate]] = {
    for {
      txId      <- registrationRepository.retrieveTransactionId(regId)
      incStatus <- incorporationInformationConnector.checkStatus(txId, REGIME, SUBSCRIBER, CALLBACK_URL)
    } yield incStatus
  }

  def buildADesSubmission(regId: String, incorporationStatus: Option[IncorpStatusUpdate]): Future[DESSubmission  ] = {
    incorporationStatus match {
      case Some(IncorpStatusUpdate(_, "accepted", Some(_), _, _, _))  => buildPartialOrFullDesSubmission(regId, incorporationStatus)
      case Some(_)                                                    => throw new RejectedIncorporationException(s"incorporation for regId $regId has been rejected")
      case None                                                       => buildPartialOrFullDesSubmission(regId, None)
    }
  }

  private[services] def assertOrGenerateAcknowledgementReference(regId: String): Future[String]= {
    registrationRepository.retrieveAcknowledgementReference(regId) flatMap {
      case Some(ackRef) => Future.successful(ackRef)
      case None => for {
        newAckref <- generateAcknowledgementReference
        _ <- registrationRepository.saveAcknowledgementReference(regId, newAckref)
      } yield newAckref
    }
  }

  private[services] def generateAcknowledgementReference: Future[String] = {
    val sequenceID = "AcknowledgementID"
    sequenceRepository.getNext(sequenceID)
      .map(ref => f"BRPY$ref%011d")
  }

  private[services] def buildPartialOrFullDesSubmission(regId: String, incorpStatusUpdate: Option[IncorpStatusUpdate]): Future[DESSubmissionModel] = {
    registrationRepository.retrieveRegistration(regId) map {
      case Some(payeReg) if payeReg.status == PAYEStatus.draft => incorpStatusUpdate match {
        case Some(statusUpdate) =>
          Logger.info("[SubmissionService] - [buildPartialOrFullDesSubmission]: building a full DES submission")
          payeReg2DESSubmission(payeReg, statusUpdate.crn)
        case None =>
          Logger.info("[SubmissionService] - [buildPartialOrFullDesSubmission]: building a partial DES submission")
          payeReg2DESSubmission(payeReg, None)
      }
      case Some(payeReg) if payeReg.status == PAYEStatus.invalid => throw new InvalidRegistrationException(regId)
      case None =>
        Logger.warn(s"[SubmissionService] - [buildPartialDesSubmission]:  building des top submission failed, there was no registration document present for regId $regId")
        throw new MissingRegDocument(regId)
      case _ =>
        Logger.warn(s"[SubmissionService] - [buildPartialDesSubmission]: The registration for regId $regId has already been submitted")
        throw new RegistrationAlreadySubmitted(regId)
    }
  }

  private[services] def buildTopUpDESSubmission(regId: String, incorpStatusUpdate: IncorpStatusUpdate): Future[TopUpDESSubmission] = {
    registrationRepository.retrieveRegistration(regId) map {
      case Some(payeReg) if payeReg.status == PAYEStatus.held => payeReg2TopUpDESSubmission(payeReg, incorpStatusUpdate)
      case Some(payeReg) =>
        Logger.error(s"[SubmissionService] - [buildTopUpDESSubmission]: paye status is currently ${payeReg.status} for registrationId $regId")
        throw new InvalidRegistrationException(regId)
      case None =>
        Logger.error(s"[SubmissionService] - [buildTopUpDESSubmission]: building des top submission failed, there was no registration document present for regId $regId")
        throw new MissingRegDocument(regId)
    }
  }

  private def processSuccessfulDESResponse(regId: String, newStatus: PAYEStatus.Value): Future[PAYEStatus.Value] = {
    for {
      status <- registrationRepository.updateRegistrationStatus(regId, newStatus)
      _ <- registrationRepository.cleardownRegistration(regId)
    } yield status
  }

  private[services] def payeReg2DESSubmission(payeReg: PAYERegistration, incorpUpdateCrn: Option[String]): DESSubmissionModel = {
    val companyDetails = payeReg.companyDetails.getOrElse{throw new CompanyDetailsNotDefinedException}
    DESSubmissionModel(
      acknowledgementReference = payeReg.acknowledgementReference.getOrElse {
        Logger.warn(s"[SubmissionService] - [payeReg2PartialDESSubmission]: Unable to convert to Partial DES Submission model for reg ID ${payeReg.registrationID}, Error: Missing Acknowledgement Ref")
        throw new AcknowledgementReferenceNotExistsException(payeReg.registrationID)
      },
      crn = incorpUpdateCrn,
      company = buildDESCompanyDetails(companyDetails),
      directors = buildDESDirectors(payeReg.directors),
      payeContact = buildDESPAYEContact(payeReg.payeContact),
      businessContact = buildDESBusinessContact(companyDetails.businessContactDetails),
      sicCodes = buildDESSicCodes(payeReg.sicCodes),
      employment = buildDESEmploymentDetails(payeReg.employment),
      completionCapacity = buildDESCompletionCapacity(payeReg.completionCapacity)
    )
  }

  private[services] def payeReg2TopUpDESSubmission(payeReg: PAYERegistration, incorpStatusUpdate: IncorpStatusUpdate): TopUpDESSubmission = {
    TopUpDESSubmission(
      acknowledgementReference = payeReg.acknowledgementReference.getOrElse {
        Logger.warn(s"[SubmissionService] - [payeReg2TopUpDESSubmission]: Unable to convert to Top Up DES Submission model for reg ID ${payeReg.registrationID}, Error: Missing Acknowledgement Ref")
        throw new AcknowledgementReferenceNotExistsException(payeReg.registrationID)
      },
      status = incorpStatusUpdate.status,
      crn = incorpStatusUpdate.crn
    )
  }

  private def buildDESCompanyDetails(details: CompanyDetails): DESCompanyDetails = {
    DESCompanyDetails(
      companyName = details.companyName,
      tradingName = details.tradingName,
      ppob = details.ppobAddress,
      regAddress = details.roAddress
    )
  }

  private def buildDESDirectors(directors: Seq[Director]): Seq[DESDirector] = {
    directors.map(dir => {
      DESDirector(
        forename = dir.name.forename,
        surname = dir.name.surname,
        otherForenames = dir.name.otherForenames,
        title = dir.name.title,
        nino = dir.nino
      )
    })
  }

  private[services] def buildDESPAYEContact(payeContact: Option[PAYEContact]): DESPAYEContact = {
    payeContact match {
      case Some(contact) =>
        DESPAYEContact(
          name = contact.contactDetails.name,
          email = contact.contactDetails.digitalContactDetails.email,
          tel = contact.contactDetails.digitalContactDetails.phoneNumber,
          mobile = contact.contactDetails.digitalContactDetails.mobileNumber,
          correspondenceAddress = contact.correspondenceAddress
        )
      case None => throw new PAYEContactNotDefinedException
    }
  }

  private def buildDESBusinessContact(businessContactDetails: DigitalContactDetails): DESBusinessContact = {
    DESBusinessContact(
      email = businessContactDetails.email,
      tel = businessContactDetails.phoneNumber,
      mobile = businessContactDetails.mobileNumber
    )
  }

  private def buildDESSicCodes(sicsCodeSeq: Seq[SICCode]): List[DESSICCode] = {
    sicsCodeSeq.toList map { list =>
      DESSICCode(
        code = list.code,
        description = list.description
      )
    }
  }

  private[services] def buildDESEmploymentDetails(employment: Option[Employment]): DESEmployment = {
    employment match {
      case Some(details) =>
        DESEmployment(
          employees = details.employees,
          ocpn = details.companyPension,
          cis = details.subcontractors,
          firstPaymentDate = details.firstPaymentDate
        )
      case None => throw new EmploymentDetailsNotDefinedException
    }
  }

  private[services] def buildDESCompletionCapacity(capacity: Option[String]): DESCompletionCapacity = {
    val DIRECTOR = "director"
    val AGENT = "agent"
    val OTHER = "other"
    capacity.map(_.trim.toLowerCase).map {
        case DIRECTOR => DESCompletionCapacity(DIRECTOR, None)
        case AGENT => DESCompletionCapacity(AGENT, None)
        case other => DESCompletionCapacity(OTHER, Some(other))
      }.getOrElse{
        throw new CompletionCapacityNotDefinedException
      }
  }
}
