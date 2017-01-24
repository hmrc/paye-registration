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

object RegistrationMongo extends MongoDbConnection with ReactiveMongoFormats {
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
  def dropCollection: Future[Unit]
}

class RegistrationMongoRepository(mongo: () => DB, format: Format[PAYERegistration]) extends ReactiveRepository[PAYERegistration, BSONObjectID](
  collectionName = "registration-information",
  mongo = mongo,
  domainFormat = format
  ) with RegistrationRepository
    with AuthorisationResource[String] {

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
    collection.find(selector).one[PAYERegistration]
  }

  override def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]] = {
    retrieveRegistration(registrationID) map {
      case Some(registration) => registration.companyDetails
      case None =>
        Logger.warn(s"Unable to retrieve Company Details for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        None
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
        None
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

  def addRegistration(payeReg: PAYERegistration): Future[PAYERegistration] = {
    collection.insert[PAYERegistration](payeReg) map {
      res => payeReg
    } recover {
      case e => throw new InsertFailed(payeReg.registrationID, "PAYE Registration")
    }
  }

  private def newRegistrationObject(registrationID: String, internalId : String): PAYERegistration = {
    val timeStamp = DateHelper.formatTimestamp(LocalDateTime.now())
    PAYERegistration(
      registrationID = registrationID,
      internalID = internalId,
      formCreationTimestamp = timeStamp,
      companyDetails = None,
      employment = None
    )
  }
}
