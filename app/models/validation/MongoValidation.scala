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

package models.validation

import auth.CryptoSCRS
import enums.Employing
import models.Address
import models.incorporation.IncorpStatusUpdate
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.{Instant, LocalDate, LocalDateTime, ZoneId, ZoneOffset, ZonedDateTime}

object MongoValidation extends BaseJsonFormatting {
  override val phoneNumberReads        = standardRead
  override val emailAddressReads       = standardRead
  override val completionCapacityReads = standardRead
  override val nameReads               = standardRead
  override val natureOfBusinessReads   = standardRead

  override val tradingNameFormat = readToFmt(standardRead)

  //Address validation
  override val addressLineValidate  = standardRead
  override val addressLine4Validate = standardRead
  override val postcodeValidate     = standardRead
  override val countryValidate      = standardRead

  override def employmentPaymentDateFormat(incorpDate: Option[LocalDate] = None, employees: Employing.Value) =
    Format(Reads.DefaultLocalDateReads, Writes.DefaultLocalDateWrites)
  override def employmentSubcontractorsFormat(construction: Boolean): Format[Boolean] = Format(Reads.BooleanReads, Writes.BooleanWrites)
  override def employeesFormat(companyPension: Option[Boolean]): Format[Employing.Value] = Format(Reads.enumNameReads(Employing), Writes.enumNameWrites)

  override val directorNameFormat   = readToFmt(standardRead)
  override val directorTitleFormat  = readToFmt(standardRead)
  override val directorNinoFormat   = readToFmt(standardRead)

  private val dateTimeReadMongo: Reads[ZonedDateTime] =
    Reads.at[String](__ \ "$date" \ "$numberLong")
      .map(dateTime => Instant.ofEpochMilli(dateTime.toLong).atZone(ZoneOffset.UTC))

  private val dateTimeWriteMongo: Writes[ZonedDateTime] =
    Writes.at[String](__ \ "$date" \ "$numberLong")
      .contramap(_.toInstant.toEpochMilli.toString)

  override val dateFormat: Format[ZonedDateTime] = Format(dateTimeReadMongo, dateTimeWriteMongo)

  override def cryptoFormat(crypto: CryptoSCRS): Format[String] = Format(crypto.rds, crypto.wts)

  override def addressReadsWithFilter(readsDef: Reads[Address]): Reads[Address] = readsDef

  override def incorpStatusUpdateReadsWithFilter(readsDef: Reads[IncorpStatusUpdate]): Reads[IncorpStatusUpdate] = readsDef

  override val companyNameFormatter = new Format[String] {
    override def reads(json: JsValue) = Reads.StringReads.reads(json)
    override def writes(companyName: String) = Writes.StringWrites.writes(companyName)
  }

  override val crnReads: Reads[String] = Reads.StringReads
}
