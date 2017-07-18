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

import models.{Address, CompanyDetails, DigitalContactDetails, Director}
import play.api.libs.json.Writes
import play.api.libs.functional.syntax._
import play.api.libs.json._
import common.exceptions.RegistrationExceptions.CompletionCapacityNotDefinedException

object BusinessType {
  val LimitedCompany = "Limited company"
}

case class DESMetaData(sessionId: String,
                       credId: String,
                       language: String,
                       submissionTs: String,
                       completionCapacity: DESCompletionCapacity)

object DESMetaData {

  implicit val writes = new Writes[DESMetaData] {
    def writes(m: DESMetaData) = {
      Json.obj(
        "businessType" -> BusinessType.LimitedCompany,
        "submissionFromAgent" -> false,
        "declareAccurateAndComplete" -> true,
        "sessionID" -> m.sessionId,
        "credentialID" -> m.credId,
        "language" -> m.language,
        "formCreationTimestamp" -> m.submissionTs
      ) ++ Json.toJson(m.completionCapacity).as[JsObject]
    }
  }
}

case class DESLimitedCompany(companyUTR: Option[String],
                             companiesHouseCompanyName: String,
                             nameOfBusiness: Option[String],
                             businessAddress: Address,
                             businessContactDetails: DigitalContactDetails,
                             natureOfBusiness: String,
                             crn: Option[String],
                             directors: Seq[Director],
                             registeredOfficeAddress: Address,
                             operatingOccPensionScheme: Option[Boolean])

object DESLimitedCompany {
  println()

  implicit val writes: Writes[DESLimitedCompany] = (
    (__ \ "companyUTR").writeNullable[String] and
    (__ \ "companiesHouseCompanyName").write[String](CompanyDetails.companyNameForDES) and
    (__ \ "nameOfBusiness").writeNullable[String] and
    (__ \ "businessAddress").write[Address](Address.writesDES) and
    (__ \ "businessContactDetails").write[DigitalContactDetails] and
    (__ \ "natureOfBusiness").write[String] and
    (__ \ "crn").writeNullable[String] and
    (__ \ "directors").write[Seq[Director]](Director.seqWritesDES) and
    (__ \ "registeredOfficeAddress").write[Address](Address.writesDES) and
    (__ \ "operatingOccPensionScheme").writeNullable[Boolean]
  )(unlift(DESLimitedCompany.unapply))
}

case class DESEmployingPeople(dateOfFirstEXBForEmployees: LocalDate,
                              numberOfEmployeesExpectedThisYear: String,
                              engageSubcontractors: Boolean,
                              correspondenceName: String,
                              correspondenceContactDetails: DigitalContactDetails,
                              payeCorrespondenceAddress: Address)

object DESEmployingPeople {
  implicit val writes: Writes[DESEmployingPeople] = (
    (__ \ "dateOfFirstEXBForEmployees").write[LocalDate] and
    (__ \ "numberOfEmployeesExpectedThisYear").write[String] and
    (__ \ "engageSubcontractors").write[Boolean] and
    (__ \ "correspondenceName").write[String] and
    (__ \ "correspondenceContactDetails").write[DigitalContactDetails] and
    (__ \ "payeCorrespondenceAddress").write[Address](Address.writesDES)
  )(unlift(DESEmployingPeople.unapply))
}

case class DESPAYEContact(name: String,
                          email: Option[String],
                          tel: Option[String],
                          mobile: Option[String],
                          correspondenceAddress: Address)

object DESPAYEContact {
  implicit val writes: Writes[DESPAYEContact] = (
    (__ \ "name").write[String] and
    (__ \ "email").writeNullable[String] and
    (__ \ "tel").writeNullable[String] and
    (__ \ "mobile").writeNullable[String] and
    (__ \ "correspondenceAddress").write[Address]
  )(unlift(DESPAYEContact.unapply))
}

case class DESCompletionCapacity(capacity: String,
                                otherCapacity: Option[String])

object DESCompletionCapacity {
  val capitalizeWrites = new Writes[String] {
    override def writes(o: String): JsValue = Json.toJson(o.capitalize)
  }

  implicit val writes: Writes[DESCompletionCapacity] =
    (
      (__ \ "completionCapacity").write[String](capitalizeWrites) and
      (__ \ "completionCapacityOther").writeNullable[String]
    )(unlift(DESCompletionCapacity.unapply))

  val writesPreviousCC: Writes[DESCompletionCapacity] = new Writes[DESCompletionCapacity] {
    override def writes(cc: DESCompletionCapacity): JsValue = {
      val successWrites = (
        (__ \ "previousCompletionCapacity").write[String](capitalizeWrites) and
        (__ \ "previousCompletionCapacityOther").writeNullable[String]
      )(unlift(DESCompletionCapacity.unapply))

      Json.toJson(cc)(successWrites).as[JsObject]
    }
  }

  val writesNewCC: Writes[DESCompletionCapacity] = new Writes[DESCompletionCapacity] {
    override def writes(cc: DESCompletionCapacity): JsValue = {
      val successWrites = (
        (__ \ "newCompletionCapacity").write[String](capitalizeWrites) and
        (__ \ "newCompletionCapacityOther").writeNullable[String]
      )(unlift(DESCompletionCapacity.unapply))

      Json.toJson(cc)(successWrites).as[JsObject]
    }
  }

  def buildDESCompletionCapacity(capacity: Option[String]): DESCompletionCapacity = {
    val DIRECTOR = "Director"
    val AGENT = "Agent"
    val OTHER = "Other"
    capacity.map(_.trim.toLowerCase).map {
      case d if d == DIRECTOR.toLowerCase => DESCompletionCapacity(DIRECTOR, None)
      case a if a == AGENT.toLowerCase => DESCompletionCapacity(AGENT, None)
      case other => DESCompletionCapacity(OTHER, Some(other))
    }.getOrElse{
      throw new CompletionCapacityNotDefinedException("Completion capacity not defined")
    }
  }
}
