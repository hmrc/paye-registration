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

package controllers.test

import javax.inject.{Inject, Named, Singleton}
import jobs.ScheduledJob
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.controller.{BackendController, BaseController}
import utils._

import scala.concurrent.Future

@Singleton
class FeatureSwitchController @Inject()(@Named("remove-stale-documents-job") val removeStaleDocsJob: ScheduledJob,
                                        @Named("metrics-job") val graphiteMetrics: ScheduledJob,
                                        controllerComponents: ControllerComponents) extends FeatureSwitchCtrl(controllerComponents) {

}
abstract class FeatureSwitchCtrl(controllerComponents: ControllerComponents) extends BackendController(controllerComponents) {
  val removeStaleDocsJob: ScheduledJob
  val graphiteMetrics: ScheduledJob

  val fs =  FeatureSwitch

  def switch(featureName: String, featureState: String) = Action.async {
    implicit request =>

      def feature: FeatureSwitch = (featureName, featureState) match {
        case ("removeStaleDocumentsFeature", "true")  =>
          removeStaleDocsJob.scheduler.resumeJob("remove-stale-documents-job")
          BooleanFeatureSwitch("removeStaleDocumentsFeature",true)
        case ("removeStaleDocumentsFeature", "false") =>
          removeStaleDocsJob.scheduler.suspendJob("remove-stale-documents-job")
          BooleanFeatureSwitch("removeStaleDocumentsFeature",false)
        case ("graphiteMetrics", "true")              =>
          graphiteMetrics.scheduler.resumeJob("metrics-job")
          BooleanFeatureSwitch("graphiteMetrics",true)
        case ("graphiteMetrics", "false")             =>
          graphiteMetrics.scheduler.suspendJob("metrics-job")
          BooleanFeatureSwitch("graphiteMetrics",false)
        case (_, "true")                                          => fs.enable(BooleanFeatureSwitch(featureName, enabled = true))
        case (_, x) if x.matches(FeatureSwitch.datePatternRegex)  => fs.setSystemDate(ValueSetFeatureSwitch(featureName, featureState))
        case (_, x@"time-clear")  => fs.clearSystemDate(ValueSetFeatureSwitch(featureName, x))
        case _                                                    => fs.disable(BooleanFeatureSwitch(featureName, enabled = false))
      }
      PAYEFeatureSwitches(featureName) match {
        case Some(_) => Future.successful(Ok(feature.toString))
        case None => Future.successful(BadRequest)
      }
  }
}
