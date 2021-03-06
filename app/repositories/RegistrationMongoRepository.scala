/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.ZonedDateTime

import auth.{AuthorisationResource, CryptoSCRS}
import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import common.exceptions.DBExceptions._
import common.exceptions.RegistrationExceptions.AcknowledgementReferenceExistsException
import enums.PAYEStatus
import helpers.DateHelper
import javax.inject.{Inject, Singleton}
import models._
import models.validation.MongoValidation
import play.api.libs.json._
import play.api.{Configuration, Logger}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor
import reactivemongo.api.commands.{UpdateWriteResult, WriteResult}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID, _}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.universe.{TypeTag, typeOf}

@Singleton
class RegistrationMongoRepository @Inject()(metrics: Metrics,
                                            dateHelper: DateHelper,
                                            mongo: ReactiveMongoComponent,
                                            config: Configuration,
                                            cryptoSCRS: CryptoSCRS
                                           ) extends ReactiveRepository[PAYERegistration, BSONObjectID](
  collectionName = "registration-information",
  mongo = mongo.mongoConnector.db,
  domainFormat = PAYERegistration.format(crypto = cryptoSCRS, formatter = MongoValidation)
) with AuthorisationResource with ReactiveMongoFormats {

  val mongoResponseTimer: Timer = metrics.defaultRegistry.timer("mongo-call-timer")

  val MAX_STORAGE_DAYS: Int = config.getInt("constants.maxStorageDays").getOrElse(90)

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("registrationID" -> IndexType.Ascending),
      name = Some("RegId"),
      unique = true,
      sparse = false
    ),
    Index(
      key = Seq("acknowledgementReference" -> IndexType.Ascending),
      name = Some("AckRefIndex"),
      unique = false,
      sparse = false
    ),
    Index(
      key = Seq("transactionID" -> IndexType.Ascending),
      name = Some("TxId"),
      unique = true,
      sparse = false
    ),
    Index(
      key = Seq("lastAction" -> IndexType.Ascending),
      name = Some("lastActionIndex"),
      unique = false,
      sparse = false
    )
  )

  private def startupJob = getRegistrationStats() map {
    stats => Logger.info(s"[RegStats] ${stats}")
  }


  startupJob
  implicit val mongoFormat: OFormat[PAYERegistration] = OFormat.apply[PAYERegistration](j => domainFormatImplicit.reads(j), (p: PAYERegistration) => domainFormatImplicit.writes(p).as[JsObject])


  private[repositories] def registrationIDSelector(registrationID: String): BSONDocument = BSONDocument(
    "registrationID" -> BSONString(registrationID)
  )

  private[repositories] def transactionIDSelector(transactionID: String): BSONDocument = BSONDocument(
    "transactionID" -> BSONString(transactionID)
  )

  private[repositories] def ackRefSelector(ackRef: String): BSONDocument = BSONDocument(
    "acknowledgementReference" -> BSONString(ackRef)
  )

  def createNewRegistration(registrationID: String, transactionID: String, internalId: String)(implicit ec: ExecutionContext): Future[PAYERegistration] = {
    val mongoTimer = mongoResponseTimer.time()
    val newReg = newRegistrationObject(registrationID, transactionID, internalId)
    collection.insert[PAYERegistration](newReg) map {
      _ =>
        mongoTimer.stop()
        newReg
    } recover {
      case e =>
        Logger.error(s"Unable to insert new PAYE Registration for reg ID $registrationID and txId $transactionID Error: ${e.getMessage}")
        mongoTimer.stop()
        throw new InsertFailed(registrationID, "PAYERegistration")
    }
  }

  private def toCamelCase(str: String): String = str.head.toLower + str.tail

  private def fetchBlock[T: TypeTag](registrationID: String, key: String = "")(implicit ec: ExecutionContext, rds: Reads[T]): Future[Option[T]] = {
    val selectorKey = if (key.isEmpty) toCamelCase(typeOf[T].typeSymbol.name.toString) else key

    val projection = Json.obj(selectorKey -> 1)
    val mongoTimer = mongoResponseTimer.time()
    collection.find(registrationIDSelector(registrationID), projection).one[JsObject].map { doc =>
      mongoTimer.stop()
      doc.fold(throw new MissingRegDocument(registrationID)) { js =>
        (js \ selectorKey).validateOpt[T].get
      }
    } recover {
      case e: Throwable =>
        mongoTimer.stop()
        Logger.error(s"Unable to retrieve PAYERegistration for reg ID $registrationID - data block: $selectorKey, Error: retrieveRegistration threw an exception: ${e.getMessage}")
        throw new RetrieveFailed(registrationID)
    }
  }

  private[repositories] def updateBlock[T](registrationID: String, data: T, key: String = "")(implicit ec: ExecutionContext, writes: Writes[T]): Future[T] = {
    val mongoTimer = mongoResponseTimer.time()
    lazy val className = data.getClass.getSimpleName
    val selectorKey = if (key.isEmpty) toCamelCase(className) else key

    val setDoc = Json.obj("$set" -> Json.obj(selectorKey -> Json.toJson(data)))
    collection.update(registrationIDSelector(registrationID), setDoc) map { updateResult =>
      mongoTimer.stop()
      if (updateResult.n == 0) {
        Logger.error(s"[$className] updating for regId : $registrationID - No document found")
        throw new MissingRegDocument(registrationID)
      } else {
        Logger.info(s"[$className] updating for regId : $registrationID - documents modified : ${updateResult.nModified}")
        data
      }
    } recover {
      case e =>
        mongoTimer.stop()
        Logger.error(s"Unable to update ${toCamelCase(className)} for regId: $registrationID, Error: ${e.getMessage}")
        throw new UpdateFailed(registrationID, className)
    }
  }

  private def unsetElement(registrationID: String, element: String)(implicit ex: ExecutionContext): Future[Boolean] = {
    collection.findAndUpdate(registrationIDSelector(registrationID), BSONDocument("$unset" -> BSONDocument(element -> ""))) map {
      _.value.fold {
        logger.error(s"[unsetElement] - There was a problem unsetting element $element for regId $registrationID")
        throw new UpdateFailed(registrationID, element)
      } { _ =>
        Logger.info(s"[RegistrationMongoRepository] [unsetElement] element: $element was unset for regId: $registrationID successfully")
        true
      }
    }
  }


  def retrieveRegistration(registrationID: String)(implicit ec: ExecutionContext): Future[Option[PAYERegistration]] = {
    val mongoTimer = mongoResponseTimer.time()
    val selector = registrationIDSelector(registrationID)
    collection.find(selector).one[JsObject] map { found =>
      mongoTimer.stop()
      found.map(_.as[PAYERegistration](PAYERegistration.format(crypto = cryptoSCRS, formatter = MongoValidation)))
    } recover {
      case e: Throwable =>
        mongoTimer.stop()
        Logger.error(s"Unable to retrieve PAYERegistration for reg ID $registrationID, Error: retrieveRegistration threw an exception: ${e.getMessage}")
        throw new RetrieveFailed(registrationID)
    }
  }


  def retrieveRegistrationByTransactionID(transactionID: String)(implicit ec: ExecutionContext): Future[Option[PAYERegistration]] = {
    val mongoTimer = mongoResponseTimer.time()
    val selector = transactionIDSelector(transactionID)
    collection.find(selector).one[PAYERegistration] map { found =>
      mongoTimer.stop()
      found
    } recover {
      case e: Throwable =>
        mongoTimer.stop()
        Logger.error(s"Unable to retrieve PAYERegistration for transaction ID $transactionID, Error: retrieveRegistration threw an exception: ${e.getMessage}")
        throw new RetrieveFailed(transactionID)
    }
  }

  def retrieveRegistrationByAckRef(ackRef: String)(implicit ec: ExecutionContext): Future[Option[PAYERegistration]] = {
    val mongoTimer = mongoResponseTimer.time()
    val selector = ackRefSelector(ackRef)
    collection.find(selector).one[PAYERegistration] map { found =>
      mongoTimer.stop()
      found
    } recover {
      case e: Throwable =>
        mongoTimer.stop()
        Logger.error(s"Unable to retrieve PAYERegistration for ack ref $ackRef, Error: retrieveRegistration threw an exception: ${e.getMessage}")
        throw new RetrieveFailed(ackRef)
    }
  }

  def retrieveCompanyDetails(registrationID: String)(implicit ec: ExecutionContext): Future[Option[CompanyDetails]] = {
    val mongoTimer = mongoResponseTimer.time()
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

  def updateRegistrationStatus(registrationID: String, payeStatus: PAYEStatus.Value)(implicit ec: ExecutionContext): Future[PAYEStatus.Value] = {
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

  def retrieveAcknowledgementReference(registrationID: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
    retrieveRegistration(registrationID) map {
      case Some(registration) => registration.acknowledgementReference
      case None =>
        Logger.error(s"[RegistrationMongoRepository] - [retrieveAcknowledgementReference]: Unable to retrieve paye registration for reg ID $registrationID, Error: Couldn't retrieve PAYE Registration")
        throw new MissingRegDocument(registrationID)
    }
  }

  def saveAcknowledgementReference(registrationID: String, ackRef: String)(implicit ec: ExecutionContext): Future[String] = {
    retrieveRegistration(registrationID) flatMap {
      case Some(registration) => registration.acknowledgementReference.isDefined match {
        case false =>
          updateRegistrationObject[String](registrationIDSelector(registrationID), registration.copy(acknowledgementReference = Some(ackRef))) {
            _ => ackRef
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

  def retrieveRegistrationStatus(registrationID: String)(implicit ec: ExecutionContext): Future[PAYEStatus.Value] = {
    val mongoTimer = mongoResponseTimer.time()
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

  def upsertCompanyDetails(registrationID: String, details: CompanyDetails)(implicit ec: ExecutionContext): Future[CompanyDetails] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(registration) =>
        updateRegistrationObject[CompanyDetails](registrationIDSelector(registrationID), registration.copy(companyDetails = Some(details))) {
          _ =>
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


  def retrieveEmploymentInfo(registrationID: String)(implicit ec: ExecutionContext): Future[Option[EmploymentInfo]] = {
    implicit val empInfoMongoFormat = EmploymentInfo.mongoFormat
    for {
      empInfo <- fetchBlock[EmploymentInfo](registrationID)
      _ <- unsetElement(registrationID, "employment")
    } yield empInfo
  }

  def upsertEmploymentInfo(registrationID: String, empInfo: EmploymentInfo)(implicit ec: ExecutionContext): Future[EmploymentInfo] = {
    updateBlock[EmploymentInfo](registrationID, empInfo)
  }

  def retrieveDirectors(registrationID: String)(implicit ec: ExecutionContext): Future[Seq[Director]] = {
    val mongoTimer = mongoResponseTimer.time()
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

  def upsertDirectors(registrationID: String, directors: Seq[Director])(implicit ec: ExecutionContext): Future[Seq[Director]] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        updateRegistrationObject[Seq[Director]](registrationIDSelector(registrationID), reg.copy(directors = directors)) {
          _ =>
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

  def retrieveSICCodes(registrationID: String)(implicit ec: ExecutionContext): Future[Seq[SICCode]] = {
    val mongoTimer = mongoResponseTimer.time()
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

  def upsertSICCodes(registrationID: String, sicCodes: Seq[SICCode])(implicit ec: ExecutionContext): Future[Seq[SICCode]] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        updateRegistrationObject[Seq[SICCode]](registrationIDSelector(registrationID), reg.copy(sicCodes = sicCodes)) {
          _ =>
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

  def retrievePAYEContact(registrationID: String)(implicit ec: ExecutionContext): Future[Option[PAYEContact]] = {
    val mongoTimer = mongoResponseTimer.time()
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

  def upsertPAYEContact(registrationID: String, payeContact: PAYEContact)(implicit ec: ExecutionContext): Future[PAYEContact] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        updateRegistrationObject[PAYEContact](registrationIDSelector(registrationID), reg.copy(payeContact = Some(payeContact))) {
          _ =>
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

  def retrieveCompletionCapacity(registrationID: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
    val mongoTimer = mongoResponseTimer.time()
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

  def upsertCompletionCapacity(registrationID: String, capacity: String)(implicit ec: ExecutionContext): Future[String] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistration(registrationID) flatMap {
      case Some(reg) =>
        updateRegistrationObject[String](registrationIDSelector(registrationID), reg.copy(completionCapacity = Some(capacity))) {
          _ =>
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

  def cleardownRegistration(registrationID: String)(implicit ec: ExecutionContext): Future[PAYERegistration] = {
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

  def updateRegistrationEmpRef(ackRef: String, applicationStatus: PAYEStatus.Value, etmpRefNotification: EmpRefNotification)(implicit ec: ExecutionContext): Future[EmpRefNotification] = {
    val mongoTimer = mongoResponseTimer.time()
    retrieveRegistrationByAckRef(ackRef) flatMap {
      case Some(regDoc) =>
        updateRegistrationObject[EmpRefNotification](ackRefSelector(ackRef), regDoc.copy(registrationConfirmation = Some(etmpRefNotification), status = applicationStatus)) {
          _ =>
            mongoTimer.stop()
            etmpRefNotification
        } recover {
          case e =>
            Logger.warn(s"Unable to update emp ref for ack ref $ackRef, Error: ${e.getMessage}")
            mongoTimer.stop()
            throw new UpdateFailed(ackRef, "Acknowledgement reference")
        }
      case None =>
        Logger.warn(s"Unable to update emp ref for ack ref $ackRef, Error: Couldn't retrieve an existing registration with that ack ref")
        mongoTimer.stop()
        throw new MissingRegDocument(ackRef)
    }
  }

  def getRegistrationId(txId: String)(implicit ec: ExecutionContext): Future[String] = {
    val projection = BSONDocument("registrationID" -> 1, "_id" -> 0)
    collection.find(transactionIDSelector(txId), projection).one[JsValue] map {
      _.fold(throw new MissingRegDocument(txId))(_.\("registrationID").validate[String].fold(
        _ => throw new IllegalStateException(s"There was a problem getting the registrationId for txId $txId"),
        identity
      ))
    }
  }

  def retrieveTransactionId(registrationID: String)(implicit ec: ExecutionContext): Future[String] = {
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

  def deleteRegistration(registrationID: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val mongoTimer = mongoResponseTimer.time()
    val selector = registrationIDSelector(registrationID)
    collection.delete().one(selector) map { writeResult =>
      mongoTimer.stop()
      if (!writeResult.ok) {
        Logger.error(s"Error when deleting registration for regId: $registrationID. Error: ${reactivemongo.api.commands.WriteResult.Message}")
      }
      writeResult.ok
    }
  }

  // TODO - rename the test repo methods
  // Test endpoints

  def dropCollection(implicit ec: ExecutionContext): Future[Unit] = {
    collection.drop()
  }

  def updateRegistration(payeReg: PAYERegistration)(implicit ec: ExecutionContext): Future[PAYERegistration] = {
    val mongoTimer = mongoResponseTimer.time()
    collection.findAndUpdate[BSONDocument, PAYERegistration](registrationIDSelector(payeReg.registrationID), payeReg, fetchNewObject = true, upsert = true) map {
      _ => {
        mongoTimer.stop()
        payeReg
      }
    } recover {
      case e =>
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

  private def updateRegistrationObject[T](doc: BSONDocument, reg: PAYERegistration)(f: UpdateWriteResult => T)(implicit ec: ExecutionContext): Future[T] = {
    val timestamp = dateHelper.getTimestamp

    collection.update(doc, reg.copy(lastUpdate = dateHelper.formatTimestamp(timestamp), lastAction = Some(timestamp))).map(f)
  }

  def removeStaleDocuments()(implicit ec: ExecutionContext): Future[(ZonedDateTime, Int)] = {

    val cuttOffDate = dateHelper.getTimestamp.minusDays(MAX_STORAGE_DAYS)
    collection.remove(staleDocumentSelector(cuttOffDate)).map {
      res => (cuttOffDate, res.n)
    }
  }

  private def staleDocumentSelector(cutOffDateTime: ZonedDateTime): BSONDocument = {
    val timeSelector = BSONDocument("$lte" -> BSONDateTime(dateHelper.zonedDateTimeToMillis(cutOffDateTime)))
    val statusSelector = BSONDocument("$in" -> BSONArray(Seq(BSONString("draft"), BSONString("invalid"))))
    BSONDocument("status" -> statusSelector, "lastAction" -> timeSelector)
  }

  private def updateLastAction(reg: PAYERegistration)(implicit ec: ExecutionContext): Future[UpdateWriteResult] = {
    val res = dateHelper.zonedDateTimeFromString(reg.lastUpdate)
    collection.update(BSONDocument("registrationID" -> reg.registrationID), BSONDocument("$set" -> BSONDocument("lastAction" -> Json.toJson(res)(MongoValidation.dateFormat))))
  }

  def upsertRegTestOnly(p: PAYERegistration, w: Format[PAYERegistration] = domainFormatImplicit)(implicit ec: ExecutionContext): Future[WriteResult] = {
    collection.insert[JsObject](w.writes(p).as[JsObject])
  }

  def getRegistrationStats()(implicit ec: scala.concurrent.ExecutionContext): Future[Map[String, Int]] = {

    // needed to make it pick up the index
    val matchQuery: collection.PipelineOperator = collection.BatchCommands.AggregationFramework.Match(Json.obj("status" -> Json.obj("$ne" -> "")))
    val project = collection.BatchCommands.AggregationFramework.Project(Json.obj("status" -> 1, "_id" -> 0))
    // calculate the regime counts
    val group = collection.BatchCommands.AggregationFramework.Group(JsString("$status"))("count" -> collection.BatchCommands.AggregationFramework.SumValue(1))

    val query = collection.aggregateWith[JsObject]()(_ => (matchQuery, List(project, group)))
    val fList = query.collect(Int.MaxValue, Cursor.FailOnError[List[JsObject]]())
    fList.map {
      _.map {
        documentWithStatusAndCount => {
          val status = (documentWithStatusAndCount \ "_id").as[String]
          val count = (documentWithStatusAndCount \ "count").as[Int]
          status -> count
        }
      }.toMap
    }
  }
}
