/*
 * Copyright 2018 HM Revenue & Customs
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

import models.validation.{APIValidation, BaseJsonFormatting, DesValidation}
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
  implicit val writes: Writes[Address] = writes(APIValidation)

  def writes(formatter: BaseJsonFormatting): Writes[Address] = {
    val ignore = OWrites[Any](_ => Json.obj())

    formatter match {
      case DesValidation => (
        (__ \ "addressLine1").write[String] and
        (__ \ "addressLine2").write[String] and
        (__ \ "addressLine3").writeNullable[String] and
        (__ \ "addressLine4").writeNullable[String] and
        (__ \ "postcode").writeNullable[String] and
        (__ \ "country").writeNullable[String] and
        ignore
      )(unlift(Address.unapply))
      case _ => Json.writes[Address]
    }
  }

  def reads(formatter: BaseJsonFormatting): Reads[Address] = {
    val readsDef = (
      (__ \ "line1").read[String](formatter.addressLineValidate) and
      (__ \ "line2").read[String](formatter.addressLineValidate) and
      (__ \ "line3").readNullable[String](formatter.addressLineValidate) and
      (__ \ "line4").readNullable[String](formatter.addressLine4Validate) and
      (__ \ "postCode").readNullable[String](formatter.postcodeValidate) and
      (__ \ "country").readNullable[String](formatter.countryValidate) and
      (__ \ "auditRef").readNullable[String]
    )(Address.apply _)

    formatter.addressReadsWithFilter(readsDef)
  }
}

object CompanyDetails {
  def format(formatter: BaseJsonFormatting): Format[CompanyDetails] = (
    (__ \ "companyName").format[String](formatter.companyNameFormatter) and
    (__ \ "tradingName").formatNullable[String](formatter.tradingNameFormat) and
    (__ \ "roAddress").format[Address](Address.reads(formatter)) and
    (__ \ "ppobAddress").format[Address](Address.reads(formatter)) and
    (__ \ "businessContactDetails").format[DigitalContactDetails](DigitalContactDetails.reads(formatter))
  )(CompanyDetails.apply, unlift(CompanyDetails.unapply))

  implicit val format: Format[CompanyDetails] = format(APIValidation)
}
