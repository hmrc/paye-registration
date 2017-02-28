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
import javax.inject.Singleton
import common.exceptions.DBExceptions._
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
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class RegistrationMongo() extends MongoDbConnection with ReactiveMongoFormats {
  val registrationFormat: Format[PAYERegistration] = Json.format[PAYERegistration]
  val store = new RegistrationMongoRepository(db, registrationFormat)
}

trait RegistrationRepository {
  def createNewRegistration(registrationID: String, internalId : String): Future[PAYERegistration]
  def retrieveRegistration(registrationID: String): Future[Option[PAYERegistration]]
  def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]]
  def upsertCompanyDetails(registrationID: String, details: CompanyDetails): Future[CompanyDetails]
  def retrieveEmployment(registrationID: String): Future[Option[Employment]]
  def upsertEmployment(registrationID: String, details: Employment): Future[Employment]
  def retrieveDirectors(registrationID: String): Future[Seq[Director]]
  def upsertDirectors(registrationID: String, directors: Seq[Director]): Future[Seq[Director]]
  def retrieveSICCodes(registrationID: String): Future[Seq[SICCode]]
  def upsertSICCodes(registrationID: String, sicCodes: Seq[SICCode]): Future[Seq[SICCode]]
  def retrievePAYEContact(registrationID: String): Future[Option[PAYEContact]]
  def upsertPAYEContact(registrationID: String, contactDetails: PAYEContact): Future[PAYEContact]
  def retrieveCompletionCapacity(registrationID: String): Future[Option[String]]
  def upsertCompletionCapacity(registrationID: String, capacity: String): Future[String]
  def dropCollection: Future[Unit]
}

class RegistrationMongoRepository(mongo: () => DB, format: Format[PAYERegistration]) extends ReactiveRepository[PAYERegistration, BSONObjectID](
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
    val newReg = newRegistrationObject(registrationID, internalId)
    collection.insert[PAYERegistration](newReg) map {
      res => newReg
    } recover {
      case e =>
        Logger.warn(s"Unable to insert new PAYE Registration for reg ID $registrationID, Error: ${e.getMessage}")
        throw new InsertFailed(registrationID, "PAYERegistration")
    }
  }

  override def retrieveRegistration(registrationID: String): Future[Option[PAYERegistration]] = {
    val selector = registrationIDSelector(registrationID)
    collection.find(selector).one[PAYERegistration] recover {
      case e : Throwable => Logger.error(s"Unable to retrieve PAYERegistration for reg ID $registrationID, Error: retrieveRegistration threw an exception: ${e.getMessage}")
                            throw new RetrieveFailed(registrationID)
    }
  }

  override def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]] = {
    retrieveRegistration(registrationID) map {
      case Some(registration) => registration.companyDetails
      case None =>
        Logger.warn(s"Unable to retrieve Company Details for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        throw new MissingRegDocument(registrationID)
    }
  }

  override def upsertCompanyDetails(registrationID: String, details: CompanyDetails): Future[CompanyDetails] = {

    retrieveRegistration(registrationID) flatMap {
      case Some(registration) =>
        collection.update(registrationIDSelector(registrationID), registration.copy(companyDetails = Some(details))) map {
          res => details
        } recover {
          case e =>
            Logger.warn(s"Unable to update Company Details for reg ID $registrationID, Error: ${e.getMessage}")
            throw new UpdateFailed(registrationID, "CompanyDetails")
        }
      case None =>
        Logger.warn(s"Unable to update Company Details for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        throw new MissingRegDocument(registrationID)
    }

  }

  override def retrieveEmployment(registrationID: String): Future[Option[Employment]] = {
    retrieveRegistration(registrationID) map {
      case Some(registration) => registration.employment
      case None =>
        Logger.warn(s"Unable to retrieve Employment for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        throw new MissingRegDocument(registrationID)
    }
  }

  override def upsertEmployment(registrationID: String, details: Employment): Future[Employment] = {
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        collection.update(registrationIDSelector(registrationID), reg.copy(employment = Some(details))) map {
          res => details
        } recover {
          case e =>
            Logger.warn(s"Unable to update Employment for reg ID $registrationID, Error: ${e.getMessage}")
            throw new UpdateFailed(registrationID, "Employment")
        }
      case None =>
        Logger.warn(s"Unable to update Employment for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        throw new MissingRegDocument(registrationID)
    }
  }

  override def retrieveDirectors(registrationID: String): Future[Seq[Director]] = {
    retrieveRegistration(registrationID) map {
      case Some(registration) => registration.directors
      case None =>
        Logger.warn(s"Unable to retrieve Directors for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        throw new MissingRegDocument(registrationID)
    }
  }

  override def upsertDirectors(registrationID: String, directors: Seq[Director]): Future[Seq[Director]] = {
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        collection.update(registrationIDSelector(registrationID), reg.copy(directors = directors)) map {
          res => directors
        } recover {
          case e =>
            Logger.warn(s"Unable to update Directors for reg ID $registrationID, Error: ${e.getMessage}")
            throw new UpdateFailed(registrationID, "Directors")
        }
      case None =>
        Logger.warn(s"Unable to update Directors for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        throw new MissingRegDocument(registrationID)
    }
  }

  override def retrieveSICCodes(registrationID: String): Future[Seq[SICCode]] = {
    retrieveRegistration(registrationID) map {
      case Some(registration) => registration.sicCodes
      case None =>
        Logger.warn(s"Unable to retrieve SIC Codes for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        throw new MissingRegDocument(registrationID)
    }
  }

  override def upsertSICCodes(registrationID: String, sicCodes: Seq[SICCode]): Future[Seq[SICCode]] = {
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        collection.update(registrationIDSelector(registrationID), reg.copy(sicCodes = sicCodes)) map {
          res => sicCodes
        } recover {
          case e =>
            Logger.warn(s"Unable to update SIC Codes for reg ID $registrationID, Error: ${e.getMessage}")
            throw new UpdateFailed(registrationID, "SIC Codes")
        }
      case None =>
        Logger.warn(s"Unable to update SIC Codes for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        throw new MissingRegDocument(registrationID)
    }
  }

  override def retrievePAYEContact(registrationID: String): Future[Option[PAYEContact]] = {
    retrieveRegistration(registrationID) map {
      case Some(registration) => registration.payeContact
      case None =>
        Logger.warn(s"Unable to retrieve Contact Details for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        throw new MissingRegDocument(registrationID)
    }
  }

  override def upsertPAYEContact(registrationID: String, contactDetails: PAYEContact): Future[PAYEContact] = {
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        collection.update(registrationIDSelector(registrationID), reg.copy(payeContact = Some(contactDetails))) map {
          res => contactDetails
        } recover {
          case e =>
            Logger.warn(s"Unable to update Contact Details for reg ID $registrationID, Error: ${e.getMessage}")
            throw new UpdateFailed(registrationID, "SIC Codes")
        }
      case None =>
        Logger.warn(s"Unable to update Contact Details for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        throw new MissingRegDocument(registrationID)
    }
  }

  override def retrieveCompletionCapacity(registrationID: String): Future[Option[String]] = {
    retrieveRegistration(registrationID) map {
      case Some(registration) => registration.completionCapacity
      case None =>
        Logger.warn(s"Unable to retrieve Completion Capacity for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        throw new MissingRegDocument(registrationID)
    }
  }

  override def upsertCompletionCapacity(registrationID: String, capacity: String): Future[String] = {
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        collection.update(registrationIDSelector(registrationID), reg.copy(completionCapacity = Some(capacity))) map {
          res => capacity
        } recover {
          case e =>
            Logger.warn(s"Unable to update Completion Capacity for reg ID $registrationID, Error: ${e.getMessage}")
            throw new UpdateFailed(registrationID, "SIC Codes")
        }
      case None =>
        Logger.warn(s"Unable to update Completion Capacity for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        throw new MissingRegDocument(registrationID)
    }
  }

  override def getInternalId(id: String)(implicit hc : HeaderCarrier) : Future[Option[(String, String)]] = {
    retrieveRegistration(id) map {
      case Some(registration) => Some(id -> registration.internalID)
      case None => None
    }
  }

  // TODO - rename the test repo methods
  // Test endpoints

  override def dropCollection: Future[Unit] = {
    collection.drop()
  }

  def deleteRegistration(registrationID: String): Future[Boolean] = {
    val selector = registrationIDSelector(registrationID)
    collection.remove(selector) map {
      res => true
    } recover {
      case e => throw new DeleteFailed(registrationID, e.getMessage)
    }
  }

  def updateRegistration(payeReg: PAYERegistration): Future[PAYERegistration] = {
    collection.findAndUpdate[BSONDocument, PAYERegistration](registrationIDSelector(payeReg.registrationID), payeReg, fetchNewObject = true, upsert = true) map {
      res => {
        payeReg
      }
    } recover {
      case e => throw new InsertFailed(payeReg.registrationID, "PAYE Registration")
    }
  }

  private def newRegistrationObject(registrationID: String, internalId : String): PAYERegistration = {
    val timeStamp = formatTimestamp(LocalDateTime.now())
    PAYERegistration(
      registrationID = registrationID,
      internalID = internalId,
      formCreationTimestamp = timeStamp,
      completionCapacity = None,
      companyDetails = None,
      directors = Seq.empty,
      payeContact = None,
      employment = None,
      sicCodes = Seq.empty
    )
  }
}
