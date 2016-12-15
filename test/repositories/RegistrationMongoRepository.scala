/*
 * Copyright 2016 HM Revenue & Customs
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

import auth.{AuthorisationResource, Crypto}
import models._
import play.api.libs.functional.syntax.unlift
import play.api.libs.json.Reads.maxLength
import play.api.libs.json.{__, Json, JsObject, JsValue}
import reactivemongo.api.DB
import reactivemongo.bson.BSONDocument
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{IndexType, Index}
import reactivemongo.bson._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NoStackTrace

object RegistrationMongo {
  implicit val formatContactDetails = ContactDetails.format
}

trait RegistrationRepository extends Repository[ContactDetails, BSONObjectID]{
  def retrieveContactDetails(registrationID: String): Future[Option[ContactDetails]]
}

private[repositories] class MissingRegDocument(regId: String) extends NoStackTrace

class CorporationTaxRegistrationMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[ContactDetails, BSONObjectID]("registration-information", mongo, ContactDetails.format, ReactiveMongoFormats.objectIdFormats)
    with RegistrationRepository
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

  override def retrieveRegistration(registrationID: String): Future[Option[CorporationTaxRegistration]] = {
    val selector = registrationIDSelector(registrationID)
    collection.find(selector).one[CorporationTaxRegistration]
  }

  override def retrieveContactDetails(registrationID: String): Future[Option[ContactDetails]] = {
    retrieveRegistration(registrationID) map {
      case Some(registration) => registration.contactDetails
      case None => None
    }
  }

  def dropCollection = {
    collection.drop()
  }
}
