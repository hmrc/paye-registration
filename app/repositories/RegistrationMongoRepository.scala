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
import reactivemongo.api.DB
import reactivemongo.bson.BSONDocument
import reactivemongo.api.indexes.{IndexType, Index}
import reactivemongo.bson._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NoStackTrace

object RegistrationMongo {
  implicit val formatContactDetails = ContactDetails.format
  implicit val format = PAYERegistration.format
}

trait RegistrationRepository extends Repository[PAYERegistration, BSONObjectID]{
  def retrieveRegistration(registrationID: String): Future[Option[PAYERegistration]]
  def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]]
}

private[repositories] class MissingRegDocument(regId: String) extends NoStackTrace

class RegistrationMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[PAYERegistration, BSONObjectID]("registration-information", mongo, PAYERegistration.format, ReactiveMongoFormats.objectIdFormats)
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

  override def retrieveRegistration(registrationID: String): Future[Option[PAYERegistration]] = {
    val selector = registrationIDSelector(registrationID)
    collection.find(selector).one[PAYERegistration]
  }

  override def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]] = {
    retrieveRegistration(registrationID) map {
      case Some(registration) => registration.companyDetails
      case None => None
    }
  }

  override def getInternalId(id: String): Future[Option[(String, String)]] = ???

  def dropCollection = {
    collection.drop()
  }
}
