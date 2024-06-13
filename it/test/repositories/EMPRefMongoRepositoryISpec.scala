/*
 * Copyright 2023 HM Revenue & Customs
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
import com.codahale.metrics.MetricRegistry
import enums.PAYEStatus
import helpers.DateHelper
import itutil.MongoBaseSpec
import models.{EmpRefNotification, PAYERegistration}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters
import play.api.Configuration
import play.api.libs.json.JsObject
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EMPRefMongoRepositoryISpec extends MongoBaseSpec {

  class Setup {
    lazy val mockMetricRegistry: MetricRegistry = app.injector.instanceOf[MetricRegistry]
    lazy val mockDateHelper: DateHelper = app.injector.instanceOf[DateHelper]
    lazy val sConfig: Configuration = app.injector.instanceOf[Configuration]
    lazy val mockcryptoSCRS: CryptoSCRS = app.injector.instanceOf[CryptoSCRS]
    val repository = new RegistrationMongoRepository(mockMetricRegistry, mockDateHelper, mongoComponent, sConfig, mockcryptoSCRS)
    await(repository.dropCollection)
  }

  def setupCollection(repo: RegistrationMongoRepository, registration: PAYERegistration): Future[PAYERegistration] = {
    repo.updateRegistration(registration)
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

      await(setupCollection(repository, validAcknowledgedPAYERegistration))

      val result: EmpRefNotification = await(repository.updateRegistrationEmpRef(ackRef, PAYEStatus.acknowledged, validEmpRefNotification))
      result mustBe validEmpRefNotification

      // check the value isn't the EMP Ref when fetched direct from the DB
      val query: Bson = Filters.equal("acknowledgementReference", ackRef)

      val stored: JsObject = await(repository.collection.find[JsObject](query).head())

      (stored \ "registrationConfirmation" \ "empRef").as[String] mustNot be(empRef)

      // check that it is the EMP Ref when fetched properly
      val fetched: PAYERegistration = await(repository.retrieveRegistrationByAckRef(ackRef)).get
      fetched.registrationConfirmation.flatMap(_.empRef) mustBe Some(empRef)
    }
  }
}
