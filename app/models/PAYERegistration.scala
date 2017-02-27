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

import play.api.libs.functional.syntax._
import play.api.libs.json.{OFormat, __}

case class PAYERegistration (registrationID: String,
                             internalID : String,
                             formCreationTimestamp: String,
                             companyDetails: Option[CompanyDetails],
                             directors: Seq[Director],
                             payeContactDetails: Option[PAYEContact],
                             employment: Option[Employment],
                             sicCodes: Seq[SICCode])

object PAYERegistration extends {

  implicit val format: OFormat[PAYERegistration] = (
    (__ \ "registrationID").format[String] and
    (__ \ "internalID").format[String] and
    (__ \ "formCreationTimestamp").format[String] and
    (__ \ "companyDetails").formatNullable[CompanyDetails] and
    (__ \ "directors").format[Seq[Director]] and
    (__ \ "payeContactDetails").formatNullable[PAYEContact] and
    (__ \ "employment").formatNullable[Employment] and
    (__ \ "sicCodes").format[Seq[SICCode]]
  )(PAYERegistration.apply, unlift(PAYERegistration.unapply))

}
