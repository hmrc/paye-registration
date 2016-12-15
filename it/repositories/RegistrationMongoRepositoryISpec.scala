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

import java.util.UUID

import models._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import reactivemongo.api.commands.WriteResult
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{WithFakeApplication, UnitSpec}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class RegistrationMongoRepositoryISpec
  extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with Eventually with WithFakeApplication {

  class Setup {
    val repository = new RegistrationMongoRepository()
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  def setupCollection(repo: RegistrationMongoRepository, registration: PAYERegistration): Future[WriteResult] = {
    repo.insert(registration)
  }

  "quick test" should {
    "xxx" in new Setup {

      private val details: CompanyDetails = CompanyDetails(None, "xxx", "yyy")
      val reg = PAYERegistration(
        "12345",
        "xxx",
        Some(details)
      )

      setupCollection(repository, reg)

      val actual = await(repository.retrieveCompanyDetails("12345"))

      actual shouldBe Some(details)
    }
  }

}
