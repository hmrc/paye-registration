/*
 * Copyright 2021 HM Revenue & Customs
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

package models.validation

import enums.Employing
import play.api.Logger
import play.api.libs.json.{Format, JsValue, Reads, Writes}

import java.time.LocalDate

object DesValidation extends BaseJsonFormatting {

  override val companyNameFormatter = new Format[String] {
    override def reads(json: JsValue) = Reads.StringReads.reads(json)

    override def writes(companyName: String) = {
      val normalised = cleanseCompanyName(companyName)
      Logger.info(s"[CompanyDetailsValidator] - [companyNameForDES] - Company name before normalisation was $companyName and after; $normalised")
      Writes.StringWrites.writes(normalised)
    }
  }

  override val phoneNumberReads        = standardRead
  override val emailAddressReads       = standardRead
  override val nameReads               = standardRead
  override val natureOfBusinessReads   = standardRead
  override val completionCapacityReads = standardRead

  override val tradingNameFormat     = new Format[String] {
    override def reads(json: JsValue) = Reads.StringReads.reads(json)

    override def writes(o: String) = Writes.StringWrites.writes(o)
  }

  //Address validation
  override val addressLineValidate  = standardRead
  override val addressLine4Validate = standardRead
  override val postcodeValidate     = standardRead
  override val countryValidate      = standardRead

  @deprecated("validation for old Employment model", "SCRS-11281")
  override val firstPaymentDateFormat = Format(Reads.DefaultLocalDateReads, Writes.DefaultLocalDateWrites)

  override def employmentPaymentDateFormat(incorpDate: Option[LocalDate] = None, employees: Employing.Value) =
    Format(Reads.DefaultLocalDateReads, Writes.DefaultLocalDateWrites)
  override def employmentSubcontractorsFormat(construction: Boolean): Format[Boolean] = Format(Reads.BooleanReads, Writes.BooleanWrites)
  override def employeesFormat(companyPension: Option[Boolean]): Format[Employing.Value] = Format(Reads.enumNameReads(Employing), Writes.enumNameWrites)

  override val directorNameFormat  = readToFmt(standardRead)
  override val directorTitleFormat = readToFmt(standardRead)
  override val directorNinoFormat  = readToFmt(standardRead)
}
