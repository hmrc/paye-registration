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

package utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import play.api.libs.json.Json

sealed trait FeatureSwitch {
  def name: String
  def value: String = ""
  def enabled: Boolean
}

trait TimedFeatureSwitch extends FeatureSwitch {

  def start: Option[LocalDateTime]
  def end: Option[LocalDateTime]
  def target: LocalDateTime

  override def enabled: Boolean = (start, end) match {
    case (Some(s), Some(e)) => !target.isBefore(s) && !target.isAfter(e)
    case (None, Some(e)) => !target.isAfter(e)
    case (Some(s), None) => !target.isBefore(s)
    case (None, None) => false
  }
}

case class BooleanFeatureSwitch(name: String, enabled: Boolean) extends FeatureSwitch

case class EnabledTimedFeatureSwitch(name: String, start: Option[LocalDateTime], end: Option[LocalDateTime], target: LocalDateTime) extends TimedFeatureSwitch
case class DisabledTimedFeatureSwitch(name: String, start: Option[LocalDateTime], end: Option[LocalDateTime], target: LocalDateTime) extends TimedFeatureSwitch {
  override def enabled: Boolean = !super.enabled
}

case class ValueSetFeatureSwitch(name: String, setValue: String) extends FeatureSwitch {
  override def enabled = true
  override def value: String = setValue
}

object FeatureSwitch {

  val DisabledIntervalExtractor = """!(\S+)_(\S+)""".r
  val EnabledIntervalExtractor = """(\S+)_(\S+)""".r
  val UNSPECIFIED = "X"
  val datePatternRegex = """(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})Z"""

  private[utils] def getProperty(name: String): FeatureSwitch = {
    val value = sys.props.get(systemPropertyName(name))

    value match {
      case Some("true")                                 => BooleanFeatureSwitch(name, enabled = true)
      case Some(DisabledIntervalExtractor(start, end))  => DisabledTimedFeatureSwitch(name, toDate(start), toDate(end), SystemDate.getSystemDate)
      case Some(EnabledIntervalExtractor(start, end))   => EnabledTimedFeatureSwitch(name, toDate(start), toDate(end), SystemDate.getSystemDate)
      case Some("time-clear")                           => ValueSetFeatureSwitch(name, "time-clear")
      case Some(date) if date.matches(datePatternRegex) => ValueSetFeatureSwitch(name, date)
      case _  if name == "system-date"                  => ValueSetFeatureSwitch(name, "time-clear")
      case _                                            => BooleanFeatureSwitch(name, enabled = false)
    }
  }

  private[utils] def setProperty(name: String, value: String): FeatureSwitch = {
    sys.props += ((systemPropertyName(name), value))
    getProperty(name)
  }

  private[utils] def toDate(text: String) : Option[LocalDateTime] = {
    text match {
      case UNSPECIFIED => None
      case _ => Some(LocalDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    }
  }

  private[utils] def systemPropertyName(name: String) = s"feature.$name"

  def enable(fs: FeatureSwitch): FeatureSwitch = setProperty(fs.name, "true")
  def disable(fs: FeatureSwitch): FeatureSwitch = setProperty(fs.name, "false")

  def setSystemDate(fs: FeatureSwitch): FeatureSwitch   = setProperty(fs.name, fs.value)
  def clearSystemDate(fs: FeatureSwitch): FeatureSwitch = setProperty(fs.name, "")

  def apply(name: String, enabled: Boolean = false): FeatureSwitch = getProperty(name)
  def unapply(fs: FeatureSwitch): Option[(String, Boolean)] = Some(fs.name -> fs.enabled)

  implicit val formats = Json.format[FeatureSwitch]
}

object PAYEFeatureSwitches extends PAYEFeatureSwitches {
  val desServiceFeature: String = "desServiceFeature"
  val removeStaleDocumentsFeature: String = "removeStaleDocumentsFeature"
  val graphiteMetricsFeature: String = "graphiteMetrics"
  val setSystemDate: String = "system-date"
}

trait PAYEFeatureSwitches {

  val desServiceFeature: String
  val removeStaleDocumentsFeature: String
  val graphiteMetricsFeature: String
  val setSystemDate: String

  def desService: FeatureSwitch = FeatureSwitch.getProperty(desServiceFeature)
  def removeStaleDocuments: FeatureSwitch = FeatureSwitch.getProperty(removeStaleDocumentsFeature)
  def graphiteMetrics: FeatureSwitch = FeatureSwitch.getProperty(graphiteMetricsFeature)
  def systemDate: FeatureSwitch = FeatureSwitch.getProperty(setSystemDate)

  def apply(name: String): Option[FeatureSwitch] = name match {
    case `desServiceFeature` => Some(desService)
    case `removeStaleDocumentsFeature` => Some(removeStaleDocuments)
    case `graphiteMetricsFeature` => Some(graphiteMetrics)
    case `setSystemDate` => Some(systemDate)
    case _ => None
  }
}
