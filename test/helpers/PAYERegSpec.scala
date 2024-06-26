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

package helpers

import mocks.PAYEMocks
import org.mockito.Mockito.reset
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec

trait PAYERegSpec extends PlaySpec with MockitoSugar with PAYEMocks with BeforeAndAfterEach with BeforeAndAfterAll {
  override def beforeEach(): Unit = {
    reset(
      mockRegistrationRepository,
      mockSequenceRepository,
      mockAuthConnector,
      mockPlayConfiguraton,
      mockCrypto
    )
  }

  override def beforeAll(): Unit = System.clearProperty("feature.system-date")

}
