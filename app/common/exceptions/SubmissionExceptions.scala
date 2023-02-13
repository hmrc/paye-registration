/*
 * Copyright 2023 HM Revenue & Customs
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

object SubmissionExceptions {
  case class RegistrationInvalidStatus(regId: String, status: String) extends NoStackTrace {
    override def getMessage = s"Tried to submit registration with status $status for reg ID $regId"
  }
  class RegistrationNotYetSubmitted(regId: String) extends NoStackTrace
  class ErrorRegistrationException(regId: String, status: String) extends NoStackTrace{
    override def getMessage = s"Incorporation not processed for regId $regId because the status is $status"
  }
}
