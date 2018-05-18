
package fixtures

import enums.Employing
import models.EmploymentInfo
import utils.SystemDate

trait EmploymentInfoFixture {
  val validEmployment = EmploymentInfo(
    employees = Employing.alreadyEmploying,
    firstPaymentDate = SystemDate.getSystemDate.toLocalDate,
    construction = true,
    subcontractors = true,
    companyPension = Some(true)
  )
}