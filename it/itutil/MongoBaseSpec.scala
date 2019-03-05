package itutil

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import play.api.inject.DefaultApplicationLifecycle
import play.api.{Configuration, Environment}
import play.modules.reactivemongo.ReactiveMongoComponentImpl
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

trait MongoBaseSpec extends UnitSpec with BeforeAndAfterEach with ScalaFutures with Eventually with WithFakeApplication {


  lazy val applicationLifeCycle = new DefaultApplicationLifecycle
  val reactiveMongoComponent = new ReactiveMongoComponentImpl(fakeApplication.injector.instanceOf[Configuration],fakeApplication.injector.instanceOf[Environment], applicationLifeCycle)

}
