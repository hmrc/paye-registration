/*
 * Copyright 2020 HM Revenue & Customs
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

import auth.CryptoSCRS
import models.validation.BaseJsonFormatting
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, __}

case class EmpRefNotification(empRef: Option[String],
                              timestamp: String,
                              status: String)

object EmpRefNotification {

  def format(formatter: BaseJsonFormatting, crypto: CryptoSCRS): Format[EmpRefNotification] = (
    (__ \ "empRef").formatNullable[String](formatter.cryptoFormat(crypto)) and
    (__ \ "timestamp").format[String] and
    (__ \ "status").format[String]
  )(EmpRefNotification.apply, unlift(EmpRefNotification.unapply))
}
