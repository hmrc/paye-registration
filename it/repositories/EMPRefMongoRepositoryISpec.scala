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

import auth.CryptoSCRS
import com.kenshoo.play.metrics.Metrics
import enums.PAYEStatus
import helpers.DateHelper
import itutil.MongoBaseSpec
import models.{EmpRefNotification, PAYERegistration}
import play.api.Configuration
import play.api.libs.json.JsObject
import reactivemongo.api.commands.WriteResult
import reactivemongo.play.json.ImplicitBSONHandlers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EMPRefMongoRepositoryISpec extends MongoBaseSpec {

  class Setup {
    lazy val mockMetrics = fakeApplication.injector.instanceOf[Metrics]
    lazy val mockDateHelper = fakeApplication.injector.instanceOf[DateHelper]
    lazy val sConfig = fakeApplication.injector.instanceOf[Configuration]
    lazy val mockcryptoSCRS = fakeApplication.injector.instanceOf[CryptoSCRS]
    val mongo = new RegistrationMongo(mockMetrics, mockDateHelper, reactiveMongoComponent, sConfig, mockcryptoSCRS)
    val repository = mongo.store
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  def setupCollection(repo: RegistrationMongoRepository, registration: PAYERegistration): Future[WriteResult] = {
    repo.insert(registration)
  }

  "EMP Ref Encryption" should {

    val ackRef = "BRCT12345678910"
    val empRef = "EMP123456789"
    val timeStamp = "2017-01-01T12:00:00.00Z"
    val lastUpdate = "2017-05-09T07:58:35Z"

    val validEmpRefNotification = EmpRefNotification(
      empRef = Some(empRef),
      timestamp = timeStamp,
      status = "04"
    )

    val validAcknowledgedPAYERegistration = PAYERegistration(
      "reg12345",
      "txID123345",
      "internalID12345",
      Some(ackRef),
      None,
      Some(validEmpRefNotification),
      timeStamp,
      PAYEStatus.acknowledged,
      None,
      None,
      Nil,
      None,
      Nil,
      lastUpdate,
      partialSubmissionTimestamp = None,
      fullSubmissionTimestamp = None,
      acknowledgedTimestamp = None,
      lastAction = None,
      employmentInfo = None
    )

    "store the plain EMP Ref in encrypted form" in new Setup {
      import reactivemongo.bson.{BSONDocument, BSONInteger, BSONString}

      await(setupCollection(repository, validAcknowledgedPAYERegistration))

      val result = await(repository.updateRegistrationEmpRef(ackRef, PAYEStatus.acknowledged, validEmpRefNotification))
      result shouldBe validEmpRefNotification

      // check the value isn't the EMP Ref when fetched direct from the DB
      val query = BSONDocument("acknowledgementReference" -> BSONString(ackRef))
      val project = BSONDocument("registrationConfirmation.empRef" -> BSONInteger(1), "_id" -> BSONInteger(0))

      val stored: Option[JsObject] = await(repository.collection.find(query, project).one[JsObject])
      stored shouldBe defined
      (stored.get \ "registrationConfirmation" \ "empRef").as[String] shouldNot be(empRef)

      // check that it is the EMP Ref when fetched properly
      val fetched: PAYERegistration = await(repository.retrieveRegistrationByAckRef(ackRef)).get
      fetched.registrationConfirmation.flatMap(_.empRef) shouldBe Some(empRef)
    }
  }
}
