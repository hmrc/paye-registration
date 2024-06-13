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

package models

import enums.Employing
import models.validation.{APIValidation, BaseJsonFormatting, MongoValidation}
import play.api.libs.json._

import java.time.LocalDate

case class EmploymentInfo(employees: Employing.Value,
                          firstPaymentDate: LocalDate,
                          construction: Boolean,
                          subcontractors: Boolean,
                          companyPension: Option[Boolean])

object EmploymentInfo {
  def format(formatter: BaseJsonFormatting, incorpDate: Option[LocalDate] = None): Format[EmploymentInfo] = new Format[EmploymentInfo] {
    override def reads(json: JsValue): JsResult[EmploymentInfo] = {
      for {
        companyPension   <- (json \ "companyPension").validateOpt[Boolean]
        employees        <- (json \ "employees").validate[Employing.Value](formatter.employeesFormat(companyPension))
        firstPaymentDate <- (json \ "firstPaymentDate").validate[LocalDate](formatter.employmentPaymentDateFormat(incorpDate, employees))
        construction     <- (json \ "construction").validate[Boolean]
        subcontractors   <- (json \ "subcontractors").validate[Boolean](formatter.employmentSubcontractorsFormat(construction))
      } yield {
        EmploymentInfo(
          employees = employees,
          firstPaymentDate = firstPaymentDate,
          construction = construction,
          subcontractors = subcontractors,
          companyPension = companyPension
        )
      }
    }

    override def writes(o: EmploymentInfo): JsValue = {
      Json.obj(
        "employees" -> o.employees,
        "firstPaymentDate" -> o.firstPaymentDate,
        "construction" -> o.construction,
        "subcontractors" -> o.subcontractors
      ) ++ o.companyPension.fold(Json.obj())(cp => Json.obj("companyPension" -> cp))
    }
  }

  implicit val apiFormat: Format[EmploymentInfo] = format(APIValidation)
  lazy val mongoFormat: Format[EmploymentInfo]   = format(MongoValidation)
}