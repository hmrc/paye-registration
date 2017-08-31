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

import models.validation.{APIValidation, BaseJsonFormatting}
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class Employment(employees: Boolean,
                      companyPension: Option[Boolean],
                      subcontractors: Boolean,
                      firstPaymentDate: LocalDate)

object Employment {
  def format(formatter: BaseJsonFormatting): Format[Employment] = (
    (__ \ "employees").format[Boolean] and
    (__ \ "ocpn").formatNullable[Boolean] and
    (__ \ "cis").format[Boolean] and
    (__ \ "first-payment-date").format[LocalDate](formatter.firstPaymentDateFormat)
  )(Employment.apply, unlift(Employment.unapply))

  implicit val format: Format[Employment] = format(APIValidation)
}
