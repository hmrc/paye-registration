package itutil

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import play.api.inject.DefaultApplicationLifecycle
import play.modules.reactivemongo.ReactiveMongoComponentImpl
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{WithFakeApplication, UnitSpec}

trait MongoBaseSpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with Eventually with WithFakeApplication {

  lazy val applicationLifeCycle = new DefaultApplicationLifecycle
  val reactiveMongoComponent = new ReactiveMongoComponentImpl(fakeApplication, applicationLifeCycle)

}
