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
import java.time.ZonedDateTime

import models.validation.{APIValidation, MongoValidation}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.DateFormatter

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
                            sicCodes: Seq[SICCode],
                            lastUpdate: String,
                            partialSubmissionTimestamp: Option[String],
                            fullSubmissionTimestamp: Option[String],
                            acknowledgedTimestamp: Option[String],
                            lastAction:Option[ZonedDateTime])

object PAYERegistration extends DateFormatter{
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
    (__ \ "companyDetails").formatNullable[CompanyDetails](CompanyDetails.formatter(APIValidation)) and
    (__ \ "directors").format[Seq[Director]](Director.directorSequenceReader(APIValidation))(Director.directorSequenceWriter(APIValidation)) and
    (__ \ "payeContact").formatNullable[PAYEContact](PAYEContact.format(APIValidation)) and
    (__ \ "employment").formatNullable[Employment](Employment.format(APIValidation)) and
    (__ \ "sicCodes").format[Seq[SICCode]](SICCode.sicCodeSequenceReader(APIValidation))(SICCode.sicCodeSequenceWriter(APIValidation)) and
    (__ \ "lastUpdate").format[String] and
    (__ \ "partialSubmissionTimestamp").formatNullable[String] and
    (__ \ "fullSubmissionTimestamp").formatNullable[String] and
    (__ \ "acknowledgedTimestamp").formatNullable[String] and
    ( __ \ "lastAction").formatNullable[ZonedDateTime](apiFormat)
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
    (__ \ "companyDetails").formatNullable[CompanyDetails](CompanyDetails.formatter(MongoValidation)) and
    (__ \ "directors").format[Seq[Director]](Director.directorSequenceReader(MongoValidation))(Director.directorSequenceWriter(MongoValidation)) and
    (__ \ "payeContact").formatNullable[PAYEContact](PAYEContact.format(MongoValidation)) and
    (__ \ "employment").formatNullable[Employment](Employment.format(MongoValidation)) and
    (__ \ "sicCodes").format[Seq[SICCode]](SICCode.sicCodeSequenceReader(MongoValidation))(SICCode.sicCodeSequenceWriter(MongoValidation)) and
    (__ \ "lastUpdate").format[String] and
    (__ \ "partialSubmissionTimestamp").formatNullable[String] and
    (__ \ "fullSubmissionTimestamp").formatNullable[String] and
    (__ \ "acknowledgedTimestamp").formatNullable[String] and
    (__ \ "lastAction").formatNullable[ZonedDateTime](mongoFormat)
  )(PAYERegistration.apply, unlift(PAYERegistration.unapply))
}
