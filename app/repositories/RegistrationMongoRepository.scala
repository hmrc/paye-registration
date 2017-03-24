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

package repositories

import java.time.LocalDateTime

import auth.AuthorisationResource
import javax.inject.{Inject, Singleton}

import common.exceptions.DBExceptions._
import common.exceptions.RegistrationExceptions.AcknowledgementReferenceExistsException
import enums.PAYEStatus
import helpers.DateHelper
import models._
import play.api.Logger
import play.api.libs.json.{Format, Json}
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.bson.BSONDocument
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson._
import reactivemongo.api.DB
import reactivemongo.bson.BSONObjectID
import services.MetricsService
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class RegistrationMongo @Inject()(injMetrics: MetricsService) extends MongoDbConnection with ReactiveMongoFormats {
  val registrationFormat: Format[PAYERegistration] = Json.format[PAYERegistration]
  val store = new RegistrationMongoRepository(db, registrationFormat, injMetrics)
}

trait RegistrationRepository {
  def createNewRegistration(registrationID: String, internalId : String): Future[PAYERegistration]
  def retrieveRegistration(registrationID: String): Future[Option[PAYERegistration]]
  def retrieveRegistrationStatus(registrationID: String): Future[PAYEStatus.Value]
  def updateRegistrationStatus(registrationID: String, status: PAYEStatus.Value): Future[PAYEStatus.Value]
  def retrieveAcknowledgementReference(registrationID: String): Future[Option[String]]
  def saveAcknowledgementReference(registrationID: String, ackRef: String): Future[String]
  def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]]
  def upsertCompanyDetails(registrationID: String, details: CompanyDetails): Future[CompanyDetails]
  def retrieveEmployment(registrationID: String): Future[Option[Employment]]
  def upsertEmployment(registrationID: String, details: Employment): Future[PAYERegistration]
  def retrieveDirectors(registrationID: String): Future[Seq[Director]]
  def upsertDirectors(registrationID: String, directors: Seq[Director]): Future[Seq[Director]]
  def retrieveSICCodes(registrationID: String): Future[Seq[SICCode]]
  def upsertSICCodes(registrationID: String, sicCodes: Seq[SICCode]): Future[Seq[SICCode]]
  def retrievePAYEContact(registrationID: String): Future[Option[PAYEContact]]
  def upsertPAYEContact(registrationID: String, contactDetails: PAYEContact): Future[PAYEContact]
  def retrieveCompletionCapacity(registrationID: String): Future[Option[String]]
  def upsertCompletionCapacity(registrationID: String, capacity: String): Future[String]
  def dropCollection: Future[Unit]
  def cleardownRegistration(registrationID: String): Future[PAYERegistration]
}

class RegistrationMongoRepository(mongo: () => DB, format: Format[PAYERegistration], metricsService: MetricsService) extends ReactiveRepository[PAYERegistration, BSONObjectID](
  collectionName = "registration-information",
  mongo = mongo,
  domainFormat = format
  ) with RegistrationRepository
    with AuthorisationResource[String]
    with DateHelper {

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("registrationID" -> IndexType.Ascending),
      name = Some("RegId"),
      unique = true,
      sparse = false
    )
  )

  private[repositories] def registrationIDSelector(registrationID: String): BSONDocument = BSONDocument(
    "registrationID" -> BSONString(registrationID)
  )

  override def createNewRegistration(registrationID: String, internalId : String): Future[PAYERegistration] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    val newReg = newRegistrationObject(registrationID, internalId)
    collection.insert[PAYERegistration](newReg) map {
      res =>
        mongoTimer.stop()
        newReg
    } recover {
      case e =>
        Logger.error(s"Unable to insert new PAYE Registration for reg ID $registrationID, Error: ${e.getMessage}")
        mongoTimer.stop()
        throw new InsertFailed(registrationID, "PAYERegistration")
    }
  }

  override def retrieveRegistration(registrationID: String): Future[Option[PAYERegistration]] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    val selector = registrationIDSelector(registrationID)
    collection.find(selector).one[PAYERegistration] map { found =>
      mongoTimer.stop()
      found
    } recover {
      case e : Throwable =>
        mongoTimer.stop()
        Logger.error(s"Unable to retrieve PAYERegistration for reg ID $registrationID, Error: retrieveRegistration threw an exception: ${e.getMessage}")
        throw new RetrieveFailed(registrationID)
    }
  }

  override def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    retrieveRegistration(registrationID) map {
      case Some(registration) =>
        mongoTimer.stop()
        registration.companyDetails
      case None =>
        Logger.error(s"Unable to retrieve Company Details for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  override def updateRegistrationStatus(registrationID: String, payeStatus: PAYEStatus.Value): Future[PAYEStatus.Value] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(regDoc) =>
        collection.update(registrationIDSelector(registrationID), regDoc.copy(status = payeStatus)) map {
          _ =>
            mongoTimer.stop()
            payeStatus
        } recover {
          case e =>
            Logger.error(s"Unable to update registration status for reg ID $registrationID, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(registrationID, "Registration status")
        }
      case None =>
        Logger.error(s"Unable to update registration status for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }

  }

  override def retrieveAcknowledgementReference(registrationID: String): Future[Option[String]] = {
    retrieveRegistration(registrationID) map {
      case Some(registration) => registration.acknowledgementReference
      case None =>
        Logger.error(s"[RegistrationMongoRepository] - [retrieveAcknowledgementReference]: Unable to retrieve paye registration for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        throw new MissingRegDocument(registrationID)
    }
  }

  override def saveAcknowledgementReference(registrationID: String, ackRef: String): Future[String] = {
    retrieveRegistration(registrationID) flatMap  {
      case Some(registration) => registration.acknowledgementReference.isDefined match {
        case false =>
          collection.update(registrationIDSelector(registrationID), registration.copy(acknowledgementReference = Some(ackRef))) map {
            res => ackRef
          } recover {
            case e =>
              Logger.error(s"[RegistrationMongoRepository] - [saveAcknowledgementReference]: Unable to save acknowledgement reference for reg ID $registrationID, Error: ${e.getMessage}")
              throw new UpdateFailed(registrationID, "AcknowledgementReference")
          }
        case true =>
          Logger.error(s"[RegistrationMongoRepository] - [saveAcknowledgementReference]: Acknowledgement reference for $registrationID already exists")
          throw new AcknowledgementReferenceExistsException(registrationID)
      }
      case None =>
        Logger.error(s"[RegistrationMongoRepository] - [saveAcknowledgementReference]: Unable to retrieve paye registration for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        throw new MissingRegDocument(registrationID)
    }
  }

  override def retrieveRegistrationStatus(registrationID: String): Future[PAYEStatus.Value] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    retrieveRegistration(registrationID) map {
      case Some(registration) =>
        mongoTimer.stop()
        registration.status
      case None =>
        Logger.warn(s"Unable to retrieve paye registration for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  override def upsertCompanyDetails(registrationID: String, details: CompanyDetails): Future[CompanyDetails] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(registration) =>
        collection.update(registrationIDSelector(registrationID), registration.copy(companyDetails = Some(details))) map {
          res =>
            mongoTimer.stop()
            details
        } recover {
          case e =>
            Logger.warn(s"Unable to update Company Details for reg ID $registrationID, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(registrationID, "CompanyDetails")
        }
      case None =>
        Logger.warn(s"Unable to update Company Details for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }

  }

  override def retrieveEmployment(registrationID: String): Future[Option[Employment]] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    retrieveRegistration(registrationID) map {
      case Some(registration) =>
        mongoTimer.stop()
        registration.employment
      case None =>
        Logger.warn(s"Unable to retrieve Employment for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  override def upsertEmployment(registrationID: String, details: Employment): Future[PAYERegistration] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        collection.update(registrationIDSelector(registrationID), reg.copy(employment = Some(details))) map {
          res =>
            mongoTimer.stop()
            reg
        } recover {
          case e =>
            Logger.warn(s"Unable to update Employment for reg ID $registrationID, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(registrationID, "Employment")
        }
      case None =>
        Logger.warn(s"Unable to update Employment for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  override def retrieveDirectors(registrationID: String): Future[Seq[Director]] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    retrieveRegistration(registrationID) map {
      case Some(registration) =>
        mongoTimer.stop()
        registration.directors
      case None =>
        Logger.warn(s"Unable to retrieve Directors for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  override def upsertDirectors(registrationID: String, directors: Seq[Director]): Future[Seq[Director]] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        collection.update(registrationIDSelector(registrationID), reg.copy(directors = directors)) map {
          res =>
            mongoTimer.stop()
            directors
        } recover {
          case e =>
            Logger.warn(s"Unable to update Directors for reg ID $registrationID, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(registrationID, "Directors")
        }
      case None =>
        Logger.warn(s"Unable to update Directors for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  override def retrieveSICCodes(registrationID: String): Future[Seq[SICCode]] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    retrieveRegistration(registrationID) map {
      case Some(registration) =>
        mongoTimer.stop()
        registration.sicCodes
      case None =>
        Logger.warn(s"Unable to retrieve SIC Codes for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  override def upsertSICCodes(registrationID: String, sicCodes: Seq[SICCode]): Future[Seq[SICCode]] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        collection.update(registrationIDSelector(registrationID), reg.copy(sicCodes = sicCodes)) map {
          res =>
            mongoTimer.stop()
            sicCodes
        } recover {
          case e =>
            Logger.warn(s"Unable to update SIC Codes for reg ID $registrationID, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(registrationID, "SIC Codes")
        }
      case None =>
        Logger.warn(s"Unable to update SIC Codes for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  override def retrievePAYEContact(registrationID: String): Future[Option[PAYEContact]] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    retrieveRegistration(registrationID) map {
      case Some(registration) =>
        mongoTimer.stop()
        registration.payeContact
      case None =>
        Logger.warn(s"Unable to retrieve Contact Details for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  override def upsertPAYEContact(registrationID: String, payeContact: PAYEContact): Future[PAYEContact] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        collection.update(registrationIDSelector(registrationID), reg.copy(payeContact = Some(payeContact))) map {
          res =>
            mongoTimer.stop()
            payeContact
        } recover {
          case e =>
            Logger.warn(s"Unable to update Contact Details for reg ID $registrationID, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(registrationID, "PAYE Contact")
        }
      case None =>
        Logger.warn(s"Unable to update Contact Details for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  override def retrieveCompletionCapacity(registrationID: String): Future[Option[String]] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    retrieveRegistration(registrationID) map {
      case Some(registration) =>
        mongoTimer.stop()
        registration.completionCapacity
      case None =>
        Logger.warn(s"Unable to retrieve Completion Capacity for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  override def upsertCompletionCapacity(registrationID: String, capacity: String): Future[String] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        collection.update(registrationIDSelector(registrationID), reg.copy(completionCapacity = Some(capacity))) map {
          res =>
            mongoTimer.stop()
            capacity
        } recover {
          case e =>
            Logger.warn(s"Unable to update Completion Capacity for reg ID $registrationID, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(registrationID, "Completion Capacity")
        }
      case None =>
        Logger.warn(s"Unable to update Completion Capacity for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  override def cleardownRegistration(registrationID: String): Future[PAYERegistration] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        val clearedDocument = reg.copy(completionCapacity = None,
                                       companyDetails = None,
                                       directors = Nil,
                                       payeContact = None,
                                       employment = None,
                                       sicCodes = Nil)
        collection.update(
          registrationIDSelector(registrationID),
          clearedDocument
        ) map { _ =>
            mongoTimer.stop()
            clearedDocument
        } recover {
          case e =>
            Logger.warn(s"Unable to cleardown personal data for reg ID $registrationID, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(registrationID, "Cleardown Registration")
        }
      case None =>
        Logger.warn(s"Unable to cleardown personal data for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  override def getInternalId(id: String)(implicit hc : HeaderCarrier) : Future[Option[(String, String)]] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    retrieveRegistration(id) map {
      case Some(registration) =>
        mongoTimer.stop()
        Some(id -> registration.internalID)
      case None =>
        mongoTimer.stop()
        None
    }
  }

  // TODO - rename the test repo methods
  // Test endpoints

  override def dropCollection: Future[Unit] = {
    collection.drop()
  }

  def deleteRegistration(registrationID: String): Future[Boolean] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    val selector = registrationIDSelector(registrationID)
    collection.remove(selector) map {
      res =>
        mongoTimer.stop()
        true
    } recover {
      case e =>
        mongoTimer.stop()
        throw new DeleteFailed(registrationID, e.getMessage)
    }
  }

  def updateRegistration(payeReg: PAYERegistration): Future[PAYERegistration] = {
    val mongoTimer = metricsService.mongoResponseTimer.time()
    collection.findAndUpdate[BSONDocument, PAYERegistration](registrationIDSelector(payeReg.registrationID), payeReg, fetchNewObject = true, upsert = true) map {
      res => {
        mongoTimer.stop()
        payeReg
      }
    } recover {
      case e =>
        mongoTimer.stop()
        throw new InsertFailed(payeReg.registrationID, "PAYE Registration")
    }
  }

  private def newRegistrationObject(registrationID: String, internalId : String): PAYERegistration = {
    val timeStamp = formatTimestamp(LocalDateTime.now())
    PAYERegistration(
      registrationID = registrationID,
      internalID = internalId,
      acknowledgementReference = None,
      formCreationTimestamp = timeStamp,
      status = PAYEStatus.draft,
      completionCapacity = None,
      companyDetails = None,
      directors = Seq.empty,
      payeContact = None,
      employment = None,
      sicCodes = Seq.empty
    )
  }
}
