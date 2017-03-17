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

package models.submission

import java.time.LocalDate

import models.Address
import play.api.libs.json.Writes
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class DESCompanyDetails(companyName: String,
                             tradingName: Option[String],
                             ppob: Address,
                             regAddress: Address)

object DESCompanyDetails {

  implicit val writes: Writes[DESCompanyDetails] =
    (
      (__ \ "company-name").write[String] and
        (__ \ "trading-name").writeNullable[String] and
        (__ \ "ppob").write[Address] and
        (__ \ "reg-address").write[Address]
      )(unlift(DESCompanyDetails.unapply))
}

case class DESDirector(forename: Option[String],
                        surname: Option[String],
                        otherForenames: Option[String],
                        title: Option[String],
                        nino: Option[String]
)

object DESDirector {
  implicit val writes: Writes[DESDirector] =
    (
      (__ \ "forename").writeNullable[String] and
        (__ \ "surname").writeNullable[String] and
        (__ \ "other-forenames").writeNullable[String] and
        (__ \ "title").writeNullable[String] and
        (__ \ "nino").writeNullable[String]
      )(unlift(DESDirector.unapply))
}

case class DESPAYEContact(name: String,
                          email: Option[String],
                          tel: Option[String],
                          mobile: Option[String],
                          correspondenceAddress: Address)

object DESPAYEContact {
  implicit val writes: Writes[DESPAYEContact] =
    (
      (__ \ "name").write[String] and
        (__ \ "email").writeNullable[String] and
        (__ \ "tel").writeNullable[String] and
        (__ \ "mobile").writeNullable[String] and
        (__ \ "correspondence-address").write[Address]
      )(unlift(DESPAYEContact.unapply))
}

case class DESBusinessContact(email: Option[String],
                              tel: Option[String],
                              mobile: Option[String])

object DESBusinessContact {
  implicit val writes: Writes[DESBusinessContact] =
  (
      (__ \ "email").writeNullable[String] and
      (__ \ "tel").writeNullable[String] and
      (__ \ "mobile").writeNullable[String]
    )(unlift(DESBusinessContact.unapply))
}

case class DESSICCode(code: Option[String],
                       description: Option[String])

object DESSICCode {
  implicit val writes: Writes[DESSICCode] =
    (
      (__ \ "code").writeNullable[String] and
      (__ \ "description").writeNullable[String]
    )(unlift(DESSICCode.unapply))
}

case class DESEmployment(employees: Boolean,
                    ocpn: Option[Boolean],
                    cis: Boolean,
                    firstPaymentDate: LocalDate)

object DESEmployment {
  implicit val writes: Writes[DESEmployment] =
    (
      (__ \ "employees").write[Boolean] and
      (__ \ "ocpn").writeNullable[Boolean] and
      (__ \ "cis").write[Boolean] and
      (__ \ "first-payment-date").write[LocalDate]
    )(unlift(DESEmployment.unapply))
}

case class DESCompletionCapacity(capacity: String,
                                otherCapacity: Option[String])

object DESCompletionCapacity {
  implicit val writes: Writes[DESCompletionCapacity] =
    (
      (__ \ "capacity").write[String] and
      (__ \ "other-capacity").writeNullable[String]
    )(unlift(DESCompletionCapacity.unapply))
}
