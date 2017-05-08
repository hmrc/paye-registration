package repositories

import enums.PAYEStatus
import models.{Eligibility, EmpRefNotification, PAYERegistration}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import play.api.Play
import play.api.libs.json.JsObject
import reactivemongo.api.commands.WriteResult
import reactivemongo.json.ImplicitBSONHandlers
import services.MetricsService
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{WithFakeApplication, UnitSpec}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class EMPRefMongoRepositoryISpec
  extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with Eventually with WithFakeApplication {


  class Setup {
    lazy val mockMetrics = Play.current.injector.instanceOf[MetricsService]
    val mongo = new RegistrationMongo(mockMetrics)
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
      eligibility = Some(Eligibility(false, false)),
      PAYEStatus.acknowledged,
      None,
      None,
      Nil,
      None,
      None,
      Nil
    )

    "store the plain EMP Ref in encrypted form" in new Setup {
      import reactivemongo.bson.{BSONDocument, BSONInteger, BSONString}
      import ImplicitBSONHandlers._

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