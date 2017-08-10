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

package models.validation

import helpers.Validation
import play.api.data.validation.ValidationError
import play.api.libs.json._

object APIValidation extends BaseValidation {

  private def isValidPhoneNumber(phoneNumber: String): Boolean = {
    def isValidNumberCount(s: String): Boolean = phoneNumber.replaceAll(" ", "").matches("[0-9]{10,20}")
    isValidNumberCount(phoneNumber) & phoneNumber.matches(Validation.phoneNumberRegex)
  }

  override val phoneNumberValidation = Reads.StringReads.filter(ValidationError("invalid phone number pattern"))(isValidPhoneNumber)


}
