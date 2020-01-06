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

package common.exceptions

import scala.util.control.NoStackTrace

object RegistrationExceptions extends RegistrationExceptions

sealed trait SubmissionMarshallingException extends NoStackTrace {
  val msg: String
  override def getMessage = msg
}

trait RegistrationExceptions {
  class AcknowledgementReferenceExistsException(regId: String) extends NoStackTrace
  class AcknowledgementReferenceNotExistsException(regId: String) extends NoStackTrace

  class CompanyDetailsNotDefinedException(val msg: String)     extends SubmissionMarshallingException
  class PAYEContactNotDefinedException(val msg: String)        extends SubmissionMarshallingException
  class EmploymentDetailsNotDefinedException(val msg: String)  extends SubmissionMarshallingException
  class CompletionCapacityNotDefinedException(val msg: String) extends SubmissionMarshallingException
  class DirectorsNotCompletedException(val msg: String)        extends SubmissionMarshallingException
  class SICCodeNotDefinedException(val msg: String)            extends SubmissionMarshallingException

  class RegistrationFormatException(message: String) extends NoStackTrace {
    override def getMessage = message
  }

  class UnmatchedStatusException extends Exception
}
