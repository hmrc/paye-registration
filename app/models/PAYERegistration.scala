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

import enums.PAYEStatus
import java.time.ZonedDateTime

import models.validation.{APIValidation, BaseJsonFormatting, MongoValidation}
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class PAYERegistration(registrationID: String,
                            transactionID: String,
                            internalID : String,
                            acknowledgementReference: Option[String],
                            crn: Option[String],
                            registrationConfirmation: Option[EmpRefNotification],
                            formCreationTimestamp: String,
                            status: PAYEStatus.Value,
                            completionCapacity: Option[String],
                            companyDetails: Option[CompanyDetails],
                            directors: Seq[Director],
                            payeContact: Option[PAYEContact],
                            sicCodes: Seq[SICCode],
                            lastUpdate: String,
                            partialSubmissionTimestamp: Option[String],
                            fullSubmissionTimestamp: Option[String],
                            acknowledgedTimestamp: Option[String],
                            lastAction:Option[ZonedDateTime],
                            employmentInfo: Option[EmploymentInfo] = None)

object PAYERegistration {
  implicit val format: OFormat[PAYERegistration] = format(APIValidation)

  def format(formatter: BaseJsonFormatting): OFormat[PAYERegistration] = (
    (__ \ "registrationID").format[String] and
    (__ \ "transactionID").format[String] and
    (__ \ "internalID").format[String] and
    (__ \ "acknowledgementReference").formatNullable[String] and
    (__ \ "crn").formatNullable[String] and
    (__ \ "registrationConfirmation").formatNullable[EmpRefNotification](EmpRefNotification.format(formatter)) and
    (__ \ "formCreationTimestamp").format[String] and
    (__ \ "status").format[PAYEStatus.Value] and
    (__ \ "completionCapacity").formatNullable[String] and
    (__ \ "companyDetails").formatNullable[CompanyDetails](CompanyDetails.format(formatter)) and
    (__ \ "directors").format[Seq[Director]](Director.seqFormat(formatter)) and
    (__ \ "payeContact").formatNullable[PAYEContact](PAYEContact.format(formatter)) and
    (__ \ "sicCodes").format[Seq[SICCode]](SICCode.seqFormat(formatter)) and
    (__ \ "lastUpdate").format[String] and
    (__ \ "partialSubmissionTimestamp").formatNullable[String] and
    (__ \ "fullSubmissionTimestamp").formatNullable[String] and
    (__ \ "acknowledgedTimestamp").formatNullable[String] and
    (__ \ "lastAction").formatNullable[ZonedDateTime](formatter.dateFormat) and
    (__ \ "employmentInfo").formatNullable[EmploymentInfo](EmploymentInfo.format(formatter))
  )(PAYERegistration.apply, unlift(PAYERegistration.unapply))
}
