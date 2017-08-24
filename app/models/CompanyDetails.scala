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

import models.validation.{BaseJsonFormatting, DesValidation}
import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class CompanyDetails(companyName: String,
                          tradingName: Option[String],
                          roAddress: Address,
                          ppobAddress: Address,
                          businessContactDetails: DigitalContactDetails)

case class Address(line1: String,
                   line2: String,
                   line3: Option[String],
                   line4: Option[String],
                   postCode: Option[String],
                   country: Option[String] = None,
                   auditRef: Option[String] = None)

object Address {
  val writesDES: Writes[Address] = new Writes[Address] {

    val ignore = OWrites[Any](_ => Json.obj())

    override def writes(address: Address): JsValue = {
      val successWrites = (
        (__ \ "addressLine1").write[String] and
        (__ \ "addressLine2").write[String] and
        (__ \ "addressLine3").writeNullable[String] and
        (__ \ "addressLine4").writeNullable[String] and
        (__ \ "postcode").writeNullable[String] and
        (__ \ "country").writeNullable[String] and
        ignore
      )(unlift(Address.unapply))

      Json.toJson(address)(successWrites).as[JsObject]
    }
  }

  implicit val writes = Json.writes[Address]

  def reads(formatters: BaseJsonFormatting): Reads[Address] = (
    (__ \ "line1").read[String](formatters.addressLineValidate) and
    (__ \ "line2").read[String](formatters.addressLineValidate) and
    (__ \ "line3").readNullable[String](formatters.addressLineValidate) and
    (__ \ "line4").readNullable[String](formatters.addressLine4Validate) and
    (__ \ "postCode").readNullable[String](formatters.postcodeValidate) and
    (__ \ "country").readNullable[String](formatters.countryValidate) and
    (__ \ "auditRef").readNullable[String]
  )(Address.apply _).filter(ValidationError("neither postcode nor country was completed")) {
    addr => addr.postCode.isDefined || addr.country.isDefined
  }.filter(ValidationError("both postcode and country were completed")) {
    addr => !(addr.postCode.isDefined && addr.country.isDefined)
  }
}

object CompanyDetails {
  def formatter(formatter: BaseJsonFormatting) = (
    (__ \ "companyName").format[String](formatter.companyNameFormatter) and
    (__ \ "tradingName").formatNullable[String](formatter.tradingNameFormat) and
    (__ \ "roAddress").format[Address](Address.reads(formatter)) and
    (__ \ "ppobAddress").format[Address](Address.reads(formatter)) and
    (__ \ "businessContactDetails").format[DigitalContactDetails](DigitalContactDetails.reads(formatter))
  )(CompanyDetails.apply, unlift(CompanyDetails.unapply))
}
