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

package helpers

import models.validation.DesValidation
import play.api.libs.functional.syntax._
import play.api.libs.json._

class ValidationSpec extends PAYERegSpec {

  case class TestModel(cName: String, int: Int = 616)
  implicit val testModelWriter: Writes[TestModel] = (
    (__ \ "cName").write[String](DesValidation.companyNameFormatter) and
    (__ \ "int").write[Int]
  )(unlift(TestModel.unapply))

  "companyNameForDES" should {
    "normalise téśtÇõmpâñÿÑàmę in testCompanyName" in {
      val testModel = TestModel("téśtÇõmpâñÿÑàmę")

      val testJson = Json.parse(
        """
          |{
          | "cName" : "testCompanyName",
          | "int" : 616
          |}
        """.stripMargin
      )

      Json.toJson(testModel)(testModelWriter) mustBe testJson
    }

    "normalise téśtÇõmpâñÿÑàmę in testCompanyName and remove forbidden punctuation" in {
      val testModel = TestModel("téśtÇõmpæñÿÑàmę")

      val testJson = Json.parse(
        """
          |{
          | "cName" : "testCompaenyName",
          | "int" : 616
          |}
        """.stripMargin
      )

      Json.toJson(testModel)(testModelWriter) mustBe testJson
    }
  }
}
