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

package common.exceptions

import scala.util.control.NoStackTrace

object RegistrationExceptions extends RegistrationExceptions

trait RegistrationExceptions {
  class AcknowledgementReferenceExistsException(regId: String) extends NoStackTrace
  class AcknowledgementReferenceNotExistsException(regId: String) extends NoStackTrace
  class CRNNotExistsException(regId: String) extends NoStackTrace

  class CompanyDetailsNotDefinedException extends NoStackTrace
  class PAYEContactNotDefinedException extends NoStackTrace
  class EmploymentDetailsNotDefinedException extends NoStackTrace
  class CompletionCapacityNotDefinedException extends NoStackTrace
  class SICCodeNotDefinedException extends NoStackTrace

  class RegistrationFormatException(message: String) extends NoStackTrace {
    override def getMessage = message
  }
}
