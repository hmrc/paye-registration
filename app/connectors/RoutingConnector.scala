/*
 * Copyright 2026 HM Revenue & Customs
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

package connectors

import config.AppConfig
import models.incorporation.IncorpStatusUpdate
import models.submission.{ApiSubmission, TopUpDESSubmission}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RoutingConnector @Inject()(
                                  appConfig: AppConfig,
                                  desConnector: DESConnector,
                                  hipConnector: HIPConnector
                                )(implicit ec: ExecutionContext) {

  def submitToEtmp(submission: ApiSubmission, regId: String, incorpStatusUpdate: Option[IncorpStatusUpdate])
                  (implicit hc: HeaderCarrier): Future[HttpResponse] = {
    if (appConfig.useHip) {
      hipConnector.submitToHIP(submission, regId, incorpStatusUpdate)
    } else {
      desConnector.submitToDES(submission, regId, incorpStatusUpdate)
    }
  }

  def submitTopUpToEtmp(submission: TopUpDESSubmission, regId: String, txId: String)
                       (implicit hc: HeaderCarrier): Future[HttpResponse] = {
    if (appConfig.useHip) {
      hipConnector.submitTopUpToHIP(submission, regId, txId)
    } else {
      desConnector.submitTopUpToDES(submission, regId, txId)
    }
  }
}