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

import models.ContactDetails.{nameValidator, phoneValidator, emailValidator}
import models.Validation.withFilter
import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.__

case class ContactDetails(firstName: String,
                          middleName: Option[String],
                          surname: String,
                          phone: Option[String],
                          mobile: Option[String],
                          email: Option[String])

object ContactDetails extends ContactDetailsValidator {

  implicit val format = withFilter(
    ((__ \ "contactFirstName").format[String](nameValidator) and
      (__ \ "contactMiddleName").formatNullable[String](nameValidator) and
      (__ \ "contactSurname").format[String](nameValidator) and
      (__ \ "contactDaytimeTelephoneNumber").formatNullable[String](phoneValidator) and
      (__ \ "contactMobileNumber").formatNullable[String](phoneValidator) and
      (__ \ "contactEmail").formatNullable[String](emailValidator)
      )(ContactDetails.apply, unlift(ContactDetails.unapply)),
    ValidationError("Must have at least one email, phone or mobile specified")
  )(
    cD => cD.mobile.isDefined || cD.phone.isDefined || cD.email.isDefined
  )
}
