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

import helpers.{CompanyDetailsValidator, Validation}
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
  val addressLineValidate = Reads.StringReads.filter(ValidationError("invalid address line pattern"))(_.matches(Validation.addressLineRegex))
  val addressLine4Validate = Reads.StringReads.filter(ValidationError("invalid address line 4 pattern"))(_.matches(Validation.addressLine4Regex))
  val postcodeValidate = Reads.StringReads.filter(ValidationError("invalid postcode"))(_.matches(Validation.postcodeRegex))
  val countryValidate = Reads.StringReads.filter(ValidationError("invalid country"))(_.matches(Validation.countryRegex))

  implicit val writes = Json.writes[Address]

  implicit val reads: Reads[Address] = (
    (__ \ "line1").read[String](addressLineValidate) and
    (__ \ "line2").read[String](addressLineValidate) and
    (__ \ "line3").readNullable[String](addressLineValidate) and
    (__ \ "line4").readNullable[String](addressLine4Validate) and
    (__ \ "postCode").readNullable[String](postcodeValidate) and
    (__ \ "country").readNullable[String](countryValidate) and
    (__ \ "auditRef").readNullable[String]
  )(Address.apply _).filter(ValidationError("neither postcode nor country was completed")) {
    addr => addr.postCode.isDefined || addr.country.isDefined
  }.filter(ValidationError("both postcode and country were completed")) {
    addr => !(addr.postCode.isDefined && addr.country.isDefined)
  }
}

object CompanyDetails extends CompanyDetailsValidator {

  implicit val format: Format[CompanyDetails] = (
    (__ \ "companyName").format[String](companyNameValidator) and
    (__ \ "tradingName").formatNullable[String](tradingNameValidator) and
    (__ \ "roAddress").format[Address] and
    (__ \ "ppobAddress").format[Address] and
    (__ \ "businessContactDetails").format[DigitalContactDetails]
  )(CompanyDetails.apply, unlift(CompanyDetails.unapply))

  val companyDetailDESFormat: Format[CompanyDetails] = (
    (__ \ "companyName").format[String](companyNameForDES) and
    (__ \ "tradingName").formatNullable[String](tradingNameValidator) and
    (__ \ "roAddress").format[Address] and
    (__ \ "ppobAddress").format[Address] and
    (__ \ "businessContactDetails").format[DigitalContactDetails]
  )(CompanyDetails.apply, unlift(CompanyDetails.unapply))

  val mongoFormat: Format[CompanyDetails] = (
    (__ \ "companyName").format[String](companyNameValidator) and
      (__ \ "tradingName").formatNullable[String](tradingNameValidator) and
      (__ \ "roAddress").format[Address] and
      (__ \ "ppobAddress").format[Address] and
      (__ \ "businessContactDetails").format[DigitalContactDetails](DigitalContactDetails.mongoReads)
    )(CompanyDetails.apply, unlift(CompanyDetails.unapply))
}
