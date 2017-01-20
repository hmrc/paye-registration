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

import java.time.LocalDate

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class Employment(employees: Boolean,
                      companyPension: Option[Boolean],
                      subcontractors: Boolean,
                      firstPayment: FirstPayment)

case class FirstPayment(paymentMade: Boolean,
                        firstPayDate: LocalDate)

object FirstPayment {
  implicit val format =
  ((__ \ "payment-made").format[Boolean] and
    (__ \ "payment-date").format[LocalDate]
    )(FirstPayment.apply, unlift(FirstPayment.unapply))
}

object Employment {

  implicit val format =
      ((__ \ "employees").format[Boolean] and
        (__ \ "ocpn").formatNullable[Boolean] and
        (__ \ "cis").format[Boolean] and
        (__ \ "first-payment").format[FirstPayment]
      )(Employment.apply, unlift(Employment.unapply))

}
