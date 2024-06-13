/*
 * Copyright 2024 HM Revenue & Customs
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

import auth.{AuthorisationResource, CryptoSCRS}
import com.codahale.metrics.Timer
import common.exceptions.DBExceptions._
import com.codahale.metrics.MetricRegistry
import common.exceptions.RegistrationExceptions.AcknowledgementReferenceExistsException
import enums.PAYEStatus
import helpers.DateHelper
import models._
import models.validation.MongoValidation
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.ReturnDocument.AFTER
import org.mongodb.scala.model.Updates.{set, unset}
import org.mongodb.scala.model.{Accumulators, Aggregates, Filters, FindOneAndReplaceOptions, IndexModel, IndexOptions, Projections}
import org.mongodb.scala.result.UpdateResult
import play.api.libs.json._
import play.api.Configuration
import utils.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.ZonedDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegistrationMongoRepository @Inject()(metricRegistry: MetricRegistry,
                                            dateHelper: DateHelper,
                                            mongo: MongoComponent,
                                            config: Configuration,
                                            cryptoSCRS: CryptoSCRS
                                           )(implicit ec: ExecutionContext) extends PlayMongoRepository[PAYERegistration](
  mongo,
  "registration-information",
  PAYERegistration.format(crypto = cryptoSCRS, formatter = MongoValidation),
  Seq(
    IndexModel(
      ascending("registrationID"),
      IndexOptions()
        .name("RegId")
        .unique(true)
        .sparse(false)
    ),
    IndexModel(
      ascending("acknowledgementReference"),
      IndexOptions()
        .name("AckRefIndex")
        .unique(false)
        .sparse(false)
    ),
    IndexModel(
      ascending("transactionID"),
      IndexOptions()
        .name("TxId")
        .unique(true)
        .sparse(false)
    ),
    IndexModel(
      ascending("lastAction"),
      IndexOptions()
        .name("lastActionIndex")
        .unique(false)
        .sparse(false)
    )
  ),
  extraCodecs = Seq(
    Codecs.playFormatCodec[JsObject](implicitly[Format[JsObject]]),
    Codecs.playFormatCodec[ZonedDateTime](MongoValidation.dateFormat)
  )
) with AuthorisationResource with Logging {

  val mongoResponseTimer: Timer = metricRegistry.timer("mongo-call-timer")

  val MAX_STORAGE_DAYS: Int = config.getOptional[Int]("constants.maxStorageDays").getOrElse(90)

  private def startupJob = getRegistrationStats() map {
    stats => logger.info(s"[startupJob] $stats")
  }

  startupJob

  private[repositories] def registrationIDSelector(registrationID: String): Bson = equal("registrationID", registrationID)

  private[repositories] def transactionIDSelector(transactionID: String): Bson = equal("transactionID", transactionID)

  private[repositories] def ackRefSelector(ackRef: String): Bson = equal("acknowledgementReference", ackRef)

  def createNewRegistration(registrationID: String, transactionID: String, internalId: String): Future[PAYERegistration] = {
    val mongoTimer = mongoResponseTimer.time()
    val newReg = newRegistrationObject(registrationID, transactionID, internalId)
    collection.insertOne(newReg).toFuture() map {
      _ =>
        mongoTimer.stop()
        newReg
    } recover {
      case e =>
        logger.error(s"[createNewRegistration] Unable to insert new PAYE Registration for reg ID $registrationID and txId $transactionID Error: ${e.getMessage}")
        mongoTimer.stop()
        throw new InsertFailed(registrationID, "PAYERegistration")
    }
  }

  def retrieveRegistration(registrationID: String): Future[Option[PAYERegistration]] = {
    val mongoTimer = mongoResponseTimer.time()
    val selector = registrationIDSelector(registrationID)
    collection.find(selector).headOption() map { found =>
      mongoTimer.stop()
      found
    } recover {
      case e: Throwable =>
        mongoTimer.stop()
        logger.error(s"[retrieveRegistration] Unable to retrieve PAYERegistration for reg ID $registrationID, Error: retrieveRegistration threw an exception: ${e.getMessage}")
        throw new RetrieveFailed(registrationID)
    }
  }


  def retrieveRegistrationByTransactionID(transactionID: String): Future[Option[PAYERegistration]] = {
    val mongoTimer = mongoResponseTimer.time()
    val selector = transactionIDSelector(transactionID)
    collection.find(selector).headOption() map { found =>
      mongoTimer.stop()
      found
    } recover {
      case e: Throwable =>
        mongoTimer.stop()
        logger.error(s"[retrieveRegistrationByTransactionID] Unable to retrieve PAYERegistration for transaction ID $transactionID, Error: retrieveRegistration threw an exception: ${e.getMessage}")
        throw new RetrieveFailed(transactionID)
    }
  }

  def retrieveRegistrationByAckRef(ackRef: String): Future[Option[PAYERegistration]] = {
    val mongoTimer = mongoResponseTimer.time()
    val selector = ackRefSelector(ackRef)
    collection.find(selector).headOption() map { found =>
      mongoTimer.stop()
      found
    } recover {
      case e: Throwable =>
        mongoTimer.stop()
        logger.error(s"[retrieveRegistrationByAckRef] Unable to retrieve PAYERegistration for ack ref $ackRef, Error: retrieveRegistration threw an exception: ${e.getMessage}")
        throw new RetrieveFailed(ackRef)
    }
  }

  def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) map {
      case Some(registration) =>
        mongoTimer.stop()
        registration.companyDetails
      case None =>
        logger.error(s"[retrieveCompanyDetails] Unable to retrieve Company Details for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  def updateRegistrationStatus(registrationID: String, payeStatus: PAYEStatus.Value): Future[PAYEStatus.Value] = {
    val mongoTimer = mongoResponseTimer.time()
    val timestamp = dateHelper.getTimestampString
    retrieveRegistration(registrationID) flatMap {
      case Some(regDoc) =>
        val reg = payeStatus match {
          case PAYEStatus.held => regDoc.copy(status = payeStatus, partialSubmissionTimestamp = Some(timestamp))
          case PAYEStatus.submitted => regDoc.copy(status = payeStatus, fullSubmissionTimestamp = Some(timestamp))
          case acknowledged@(PAYEStatus.acknowledged | PAYEStatus.rejected) => regDoc.copy(status = acknowledged, acknowledgedTimestamp = Some(timestamp))
          case _ => regDoc.copy(status = payeStatus)
        }
        updateRegistrationObject[PAYEStatus.Value](registrationIDSelector(registrationID), reg) {
          _ =>
            mongoTimer.stop()
            payeStatus
        } recover {
          case e =>
            logger.error(s"[updateRegistrationStatus] Unable to update registration status for reg ID $registrationID, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(registrationID, "Registration status")
        }
      case None =>
        logger.error(s"[updateRegistrationStatus] Unable to update registration status for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  def retrieveAcknowledgementReference(registrationID: String): Future[Option[String]] = {
    retrieveRegistration(registrationID) map {
      case Some(registration) => registration.acknowledgementReference
      case None =>
        logger.error(s"[retrieveAcknowledgementReference] Unable to retrieve paye registration for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        throw new MissingRegDocument(registrationID)
    }
  }

  def saveAcknowledgementReference(registrationID: String, ackRef: String): Future[String] = {
    retrieveRegistration(registrationID) flatMap {
      case Some(registration) => registration.acknowledgementReference.isDefined match {
        case false =>
          updateRegistrationObject[String](registrationIDSelector(registrationID), registration.copy(acknowledgementReference = Some(ackRef))) {
            _ => ackRef
          } recover {
            case e =>
              logger.error(s"[saveAcknowledgementReference] Unable to save acknowledgement reference for reg ID $registrationID, Error: ${e.getMessage}")
              throw new UpdateFailed(registrationID, "AcknowledgementReference")
          }
        case true =>
          logger.error(s"[saveAcknowledgementReference] Acknowledgement reference for $registrationID already exists")
          throw new AcknowledgementReferenceExistsException(registrationID)
      }
      case None =>
        logger.error(s"[saveAcknowledgementReference] Unable to retrieve paye registration for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        throw new MissingRegDocument(registrationID)
    }
  }

  def retrieveRegistrationStatus(registrationID: String): Future[PAYEStatus.Value] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) map {
      case Some(registration) =>
        mongoTimer.stop()
        registration.status
      case None =>
        logger.warn(s"[retrieveRegistrationStatus] Unable to retrieve paye registration for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  def upsertCompanyDetails(registrationID: String, details: CompanyDetails): Future[CompanyDetails] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(registration) =>
        updateRegistrationObject[CompanyDetails](registrationIDSelector(registrationID), registration.copy(companyDetails = Some(details))) {
          _ =>
            mongoTimer.stop()
            details
        } recover {
          case e =>
            logger.warn(s"[upsertCompanyDetails] Unable to update Company Details for reg ID $registrationID, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(registrationID, "CompanyDetails")
        }
      case None =>
        logger.warn(s"[upsertCompanyDetails] Unable to update Company Details for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }


  def retrieveEmploymentInfo(registrationID: String): Future[Option[EmploymentInfo]] =
    retrieveRegistration(registrationID).map(_.flatMap(_.employmentInfo))

  def upsertEmploymentInfo(registrationID: String, empInfo: EmploymentInfo): Future[EmploymentInfo] = {
    val mongoTimer = mongoResponseTimer.time()

    val setDoc = set("employmentInfo", Json.toJson(empInfo))
    collection.updateOne(registrationIDSelector(registrationID), setDoc).toFuture() map { updateResult =>
      mongoTimer.stop()
      if (updateResult.getMatchedCount == 0) {
        logger.error(s"[upsertEmploymentInfo] updating for regId : $registrationID - No document found")
        throw new MissingRegDocument(registrationID)
      } else {
        logger.info(s"[upsertEmploymentInfo] updating for regId : $registrationID - documents modified : ${updateResult.getModifiedCount}")
        empInfo
      }
    } recover {
      case e =>
        mongoTimer.stop()
        logger.error(s"[upsertEmploymentInfo] Unable to update employmentInfo for regId: $registrationID, Error: ${e.getMessage}")
        throw new UpdateFailed(registrationID, "EmploymentInfo")
    }
  }

  def retrieveDirectors(registrationID: String): Future[Seq[Director]] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) map {
      case Some(registration) =>
        mongoTimer.stop()
        registration.directors
      case None =>
        logger.warn(s"[retrieveDirectors] Unable to retrieve Directors for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  def upsertDirectors(registrationID: String, directors: Seq[Director]): Future[Seq[Director]] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        updateRegistrationObject[Seq[Director]](registrationIDSelector(registrationID), reg.copy(directors = directors)) {
          _ =>
            mongoTimer.stop()
            directors
        } recover {
          case e =>
            logger.warn(s"[upsertDirectors] Unable to update Directors for reg ID $registrationID, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(registrationID, "Directors")
        }
      case None =>
        logger.warn(s"[upsertDirectors] Unable to update Directors for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  def retrieveSICCodes(registrationID: String): Future[Seq[SICCode]] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) map {
      case Some(registration) =>
        mongoTimer.stop()
        registration.sicCodes
      case None =>
        logger.warn(s"[retrieveSICCodes] Unable to retrieve SIC Codes for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  def upsertSICCodes(registrationID: String, sicCodes: Seq[SICCode]): Future[Seq[SICCode]] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        updateRegistrationObject[Seq[SICCode]](registrationIDSelector(registrationID), reg.copy(sicCodes = sicCodes)) {
          _ =>
            mongoTimer.stop()
            sicCodes
        } recover {
          case e =>
            logger.warn(s"[upsertSICCodes] Unable to update SIC Codes for reg ID $registrationID, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(registrationID, "SIC Codes")
        }
      case None =>
        logger.warn(s"[upsertSICCodes] Unable to update SIC Codes for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  def retrievePAYEContact(registrationID: String): Future[Option[PAYEContact]] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) map {
      case Some(registration) =>
        mongoTimer.stop()
        registration.payeContact
      case None =>
        logger.warn(s"[retrievePAYEContact] Unable to retrieve Contact Details for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  def upsertPAYEContact(registrationID: String, payeContact: PAYEContact): Future[PAYEContact] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        updateRegistrationObject[PAYEContact](registrationIDSelector(registrationID), reg.copy(payeContact = Some(payeContact))) {
          _ =>
            mongoTimer.stop()
            payeContact
        } recover {
          case e =>
            logger.warn(s"[upsertPAYEContact] Unable to update Contact Details for reg ID $registrationID, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(registrationID, "PAYE Contact")
        }
      case None =>
        logger.warn(s"[upsertPAYEContact] Unable to update Contact Details for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  def retrieveCompletionCapacity(registrationID: String): Future[Option[String]] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) map {
      case Some(registration) =>
        mongoTimer.stop()
        registration.completionCapacity
      case None =>
        logger.warn(s"[retrieveCompletionCapacity] Unable to retrieve Completion Capacity for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  def upsertCompletionCapacity(registrationID: String, capacity: String): Future[String] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        updateRegistrationObject[String](registrationIDSelector(registrationID), reg.copy(completionCapacity = Some(capacity))) {
          _ =>
            mongoTimer.stop()
            capacity
        } recover {
          case e =>
            logger.warn(s"[upsertCompletionCapacity] Unable to update Completion Capacity for reg ID $registrationID, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(registrationID, "Completion Capacity")
        }
      case None =>
        logger.warn(s"[upsertCompletionCapacity] Unable to update Completion Capacity for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  def cleardownRegistration(registrationID: String): Future[PAYERegistration] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        val clearedDocument = reg.copy(completionCapacity = None,
          companyDetails = None,
          directors = Nil,
          payeContact = None,
          sicCodes = Nil,
          employmentInfo = None)
        updateRegistrationObject[PAYERegistration](registrationIDSelector(registrationID), clearedDocument) {
          _ =>
            mongoTimer.stop()
            clearedDocument
        } recover {
          case e =>
            logger.warn(s"[cleardownRegistration] Unable to cleardown personal data for reg ID $registrationID, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(registrationID, "Cleardown Registration")
        }
      case None =>
        logger.warn(s"[cleardownRegistration] Unable to cleardown personal data for reg ID $registrationID, Error: Couldn't retrieve an existing registration with that ID")
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  def updateRegistrationEmpRef(ackRef: String, applicationStatus: PAYEStatus.Value, etmpRefNotification: EmpRefNotification): Future[EmpRefNotification] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistrationByAckRef(ackRef) flatMap {
      case Some(regDoc) =>
        updateRegistrationObject[EmpRefNotification](ackRefSelector(ackRef), regDoc.copy(registrationConfirmation = Some(etmpRefNotification), status = applicationStatus)) {
          _ =>
            mongoTimer.stop()
            etmpRefNotification
        } recover {
          case e =>
            logger.warn(s"[updateRegistrationEmpRef] Unable to update emp ref for ack ref $ackRef, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(ackRef, "Acknowledgement reference")
        }
      case None =>
        logger.warn(s"[updateRegistrationEmpRef] Unable to update emp ref for ack ref $ackRef, Error: Couldn't retrieve an existing registration with that ack ref")
        mongoTimer.stop()
        throw new MissingRegDocument(ackRef)
    }
  }

  def getRegistrationId(txId: String): Future[String] =
    retrieveRegistrationByTransactionID(txId).map {
      case Some(reg) => reg.registrationID
      case None => throw new MissingRegDocument(txId)
    }

  def retrieveTransactionId(registrationID: String): Future[String] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) map {
      case Some(regDoc) =>
        mongoTimer.stop()
        regDoc.transactionID
      case None =>
        mongoTimer.stop()
        throw new MissingRegDocument(registrationID)
    }
  }

  def getInternalId(id: String)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(id) map {
      case Some(registration) =>
        mongoTimer.stop()
        Some(registration.internalID)
      case None =>
        mongoTimer.stop()
        None
    }
  }

  def deleteRegistration(registrationID: String): Future[Boolean] = {
    val mongoTimer = mongoResponseTimer.time()
    val selector = registrationIDSelector(registrationID)
    collection.deleteOne(selector).toFuture() map { _ =>
      mongoTimer.stop()
      true
    } recover {
      case e: Exception => logger.error(s"[deleteRegistration] Error when deleting registration for regId: $registrationID. Error: ${e.getMessage}")
        false
    }
  }

  def dropCollection(implicit ec: ExecutionContext): Future[Boolean] =
    collection.drop().toFuture().flatMap { _ => ensureIndexes.map(_ => true) }

  def updateRegistration(payeReg: PAYERegistration): Future[PAYERegistration] = {
    val mongoTimer = mongoResponseTimer.time()
    collection.findOneAndReplace(
      registrationIDSelector(payeReg.registrationID),
      payeReg,
      FindOneAndReplaceOptions()
        .upsert(true)
        .returnDocument(AFTER)
    ).toFuture() map { _ =>
      mongoTimer.stop()
      payeReg
    } recover {
      case _ =>
        mongoTimer.stop()
        throw new InsertFailed(payeReg.registrationID, "PAYE Registration")
    }
  }

  private def newRegistrationObject(registrationID: String, transactionID: String, internalId: String): PAYERegistration = {
    val timeStamp = dateHelper.getTimestampString

    PAYERegistration(
      registrationID = registrationID,
      transactionID = transactionID,
      internalID = internalId,
      acknowledgementReference = None,
      crn = None,
      registrationConfirmation = None,
      formCreationTimestamp = timeStamp,
      status = PAYEStatus.draft,
      completionCapacity = None,
      companyDetails = None,
      directors = Seq.empty,
      payeContact = None,
      sicCodes = Seq.empty,
      lastUpdate = timeStamp,
      partialSubmissionTimestamp = None,
      fullSubmissionTimestamp = None,
      acknowledgedTimestamp = None,
      lastAction = Some(dateHelper.getTimestamp),
      employmentInfo = None
    )
  }

  private def updateRegistrationObject[T](doc: Bson, reg: PAYERegistration)(f: UpdateResult => T): Future[T] = {
    val timestamp = dateHelper.getTimestamp
    collection.replaceOne(doc, reg.copy(lastUpdate = dateHelper.formatTimestamp(timestamp), lastAction = Some(timestamp))).toFuture().map(f)
  }

  def removeStaleDocuments(): Future[(ZonedDateTime, Int)] = {
    val cuttOffDate = dateHelper.getTimestamp.minusDays(MAX_STORAGE_DAYS)
    collection.deleteMany(staleDocumentSelector(cuttOffDate)).toFuture().map {
      res => (cuttOffDate, res.getDeletedCount.toInt)
    }
  }

  private def staleDocumentSelector(cutOffDateTime: ZonedDateTime): Bson = {
    val timeSelector = Filters.lte("lastAction", cutOffDateTime)
    val statusSelector = Filters.in("status", "draft", "invalid")
    Filters.and(statusSelector, timeSelector)
  }

  def getRegistrationStats(): Future[Map[String, Int]] = {

    // needed to make it pick up the index
    val matchQuery = Aggregates.`match`(Filters.ne("status", ""))
    val project = Aggregates.project(Projections.fields(Projections.excludeId(), Projections.include("status")))
    // calculate the regime counts
    val group = Aggregates.group("$status", Accumulators.sum("count", 1))

    collection
      .aggregate[JsObject](Seq(matchQuery, project, group))
      .toFuture()
      .map {
        _.map {
          documentWithStatusAndCount =>
            val status = (documentWithStatusAndCount \ "_id").as[String]
            val count = (documentWithStatusAndCount \ "count").as[Int]
            status -> count
        }.toMap
      }
  }
}
