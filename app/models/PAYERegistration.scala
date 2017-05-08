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

import enums.PAYEStatus
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, OFormat, __}

case class PAYERegistration(registrationID: String,
                            transactionID: String,
                            internalID : String,
                            acknowledgementReference: Option[String],
                            crn: Option[String],
                            registrationConfirmation: Option[EmpRefNotification],
                            formCreationTimestamp: String,
                            eligibility: Option[Eligibility],
                            status: PAYEStatus.Value,
                            completionCapacity: Option[String],
                            companyDetails: Option[CompanyDetails],
                            directors: Seq[Director],
                            payeContact: Option[PAYEContact],
                            employment: Option[Employment],
                            sicCodes: Seq[SICCode])

object PAYERegistration extends {

  implicit val format: OFormat[PAYERegistration] = (
    (__ \ "registrationID").format[String] and
    (__ \ "transactionID").format[String] and
    (__ \ "internalID").format[String] and
    (__ \ "acknowledgementReference").formatNullable[String] and
    (__ \ "crn").formatNullable[String] and
    (__ \ "registrationConfirmation").formatNullable[EmpRefNotification](EmpRefNotification.apiFormat) and
    (__ \ "formCreationTimestamp").format[String] and
    (__ \ "eligibility").formatNullable[Eligibility] and
    (__ \ "status").format[PAYEStatus.Value] and
    (__ \ "completionCapacity").formatNullable[String] and
    (__ \ "companyDetails").formatNullable[CompanyDetails] and
    (__ \ "directors").format[Seq[Director]] and
    (__ \ "payeContact").formatNullable[PAYEContact] and
    (__ \ "employment").formatNullable[Employment] and
    (__ \ "sicCodes").format[Seq[SICCode]]
  )(PAYERegistration.apply, unlift(PAYERegistration.unapply))

  def payeRegistrationFormat(empRefFormat: Format[EmpRefNotification]): OFormat[PAYERegistration] = (
    (__ \ "registrationID").format[String] and
    (__ \ "transactionID").format[String] and
    (__ \ "internalID").format[String] and
    (__ \ "acknowledgementReference").formatNullable[String] and
    (__ \ "crn").formatNullable[String] and
    (__ \ "registrationConfirmation").formatNullable[EmpRefNotification](empRefFormat) and
    (__ \ "formCreationTimestamp").format[String] and
    (__ \ "eligibility").formatNullable[Eligibility] and
    (__ \ "status").format[PAYEStatus.Value] and
    (__ \ "completionCapacity").formatNullable[String] and
    (__ \ "companyDetails").formatNullable[CompanyDetails] and
    (__ \ "directors").format[Seq[Director]] and
    (__ \ "payeContact").formatNullable[PAYEContact] and
    (__ \ "employment").formatNullable[Employment] and
    (__ \ "sicCodes").format[Seq[SICCode]]
  )(PAYERegistration.apply, unlift(PAYERegistration.unapply))



}
