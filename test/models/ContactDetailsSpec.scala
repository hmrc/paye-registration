/*
 * Copyright 2017 HM Revenue & Customs
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

package models

import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, Json, JsSuccess}
import uk.gov.hmrc.play.test.UnitSpec

class ContactDetailsSpec extends UnitSpec with JsonFormatValidation {

  type OS = Option[String]
  type S = String

  def lineEnd(comma: Boolean) = if (comma) "," else ""

  def jsonLine(key: S, value: S): OS = jsonLine(key, value, true)

  def jsonLine(key: S, value: S, comma: Boolean): OS = Some(s""""${key}" : "${value}"${lineEnd(comma)}""")

  def jsonLine(key: S, value: OS, comma: Boolean = true): OS = value.map(v =>s""""${key}" : "${v}"${lineEnd(comma)}""")

  def j(fn: OS = Some("F"), mn: OS = None, sn: OS = Some("S"), p: OS = None, m: OS = None, e: OS = Some("a@b.c")): S = {
    val extra: S = Seq(
      jsonLine("contactFirstName", fn, false),
      jsonLine("contactMiddleName", mn, false),
      jsonLine("contactSurname", sn, false),
      jsonLine("contactDaytimeTelephoneNumber", p, false),
      jsonLine("contactMobileNumber", m, false),
      jsonLine("contactEmail", e, false)
    ).flatten.mkString(", ")
    s"""
       |{
       |  ${extra}
       |}
     """.stripMargin
  }

  "ContactDetails Model - names" should {
    "build from JSON - minimal" in {
      val json = j()
      val expected = ContactDetails("F", None, "S", None, None, Some("a@b.c"))
      val result = Json.parse(json).validate[ContactDetails]
      shouldBeSuccess(expected, result)
    }

    "build from JSON - full" in {
      val json = j(mn = Some("M"), p = Some("1"), m = Some("2"))
      val expected = ContactDetails("F", Some("M"), "S", Some("1"), Some("2"), Some("a@b.c"))
      val result = Json.parse(json).validate[ContactDetails]
      shouldBeSuccess(expected, result)
    }

    "fail if name is empty" in {
      val json = j(fn = Some(""))
      val result = Json.parse(json).validate[ContactDetails]
      shouldHaveErrors(result, JsPath() \ "contactFirstName", Seq(ValidationError("error.minLength", 1), ValidationError("error.pattern")))
    }

    "fail if name is missing" in {
      val json = j(fn = Some(""))
      val result = Json.parse(json).validate[ContactDetails]
      shouldHaveErrors(result, JsPath() \ "contactFirstName", Seq(ValidationError("error.minLength", 1), ValidationError("error.pattern")))
    }
  }

  "ContactDetails Model - names" should {

    "build from JSON - phone" in {
      val json = j(p = Some("1"), e = None)
      val expected = ContactDetails("F", None, "S", Some("1"), None, None)
      val result = Json.parse(json).validate[ContactDetails]
      shouldBeSuccess(expected, result)
    }

    "fail if email is invalid" ignore { // ignored for now due to regex clarification!
      val json = j(e = Some("foo"))
      val result = Json.parse(json).validate[ContactDetails]
      shouldHaveErrors(result, JsPath() \ "contactEmail", Seq(ValidationError("error.pattern")))
    }
  }

  "reading from json into a ContactDetails case class" should {

    "return a ContactDetails case class" in {
      val contactDetails = ContactDetails("testFirstName", None, "testSurname", None, Some("1234567890"), None)
      val jsonNoContact = Json.parse("""{"contactFirstName":"testFirstName","contactSurname":"testSurname", "contactMobileNumber":"1234567890"}""")

      val result = Json.fromJson[ContactDetails](jsonNoContact)
      result shouldBe JsSuccess(contactDetails)
    }

    "throw a json validation error" in {
      val jsonNoContact = Json.parse("""{"contactFirstName":"testFirstName","contactSurname":"testSurname"}""")

      val result = Json.fromJson[ContactDetails](jsonNoContact)
      shouldHaveErrors(result, JsPath(), Seq(ValidationError("Must have at least one email, phone or mobile specified")))
    }
  }
}
