/*
 * Copyright 2024 HM Revenue & Customs
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

import helpers.PAYERegSpec
import org.scalatest.BeforeAndAfterEach

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FeatureSwitchSpec extends PAYERegSpec with BeforeAndAfterEach {

  override def beforeEach() {
    System.clearProperty("feature.test")
  }

  val payeFeatureSwitch = PAYEFeatureSwitches
  val booleanFeatureSwitch = BooleanFeatureSwitch("test", false)

  "getProperty" should {

    "return a disabled feature switch if the system property is undefined" in {
      FeatureSwitch.getProperty("test") mustBe BooleanFeatureSwitch("test", enabled = false)
    }

    "return an enabled feature switch if the system property is defined as 'true'" in {
      System.setProperty("feature.test", "true")

      FeatureSwitch.getProperty("test") mustBe BooleanFeatureSwitch("test", enabled = true)
    }

    "return an enabled feature switch if the system property is defined as 'false'" in {
      System.setProperty("feature.test", "false")

      FeatureSwitch.getProperty("test") mustBe BooleanFeatureSwitch("test", enabled = false)
    }

    "return a EnabledTimedFeatureSwitch when the set system property is a date" in {
      System.setProperty("feature.test", "2016-05-05T14:30:00Z_2016-05-08T14:30:00Z")

      FeatureSwitch.getProperty("test") mustBe a[EnabledTimedFeatureSwitch]
    }

    "return a DisabledTimedFeatureSwitch when the set system property is a date" in {
      System.setProperty("feature.test", "!2016-05-05T14:30:00Z_2016-05-08T14:30:00Z")

      FeatureSwitch.getProperty("test") mustBe a[DisabledTimedFeatureSwitch]
    }

    "return a ValueSetFeatureSwitch when the set system property is a date" in {
      System.setProperty("feature.test", "2016-05-05T14:30:00Z")

      FeatureSwitch.getProperty("test") mustBe a[ValueSetFeatureSwitch]
    }
  }

  "systemPropertyName" should {

    "append feature. to the supplied string'" in {
      FeatureSwitch.systemPropertyName("test") mustBe "feature.test"
    }
  }

  "setProperty" should {

    "return a feature switch (testKey, false) when supplied with (testKey, testValue)" in {
      FeatureSwitch.setProperty("test", "testValue") mustBe BooleanFeatureSwitch("test", enabled = false)
    }

    "return a feature switch (testKey, true) when supplied with (testKey, true)" in {
      FeatureSwitch.setProperty("test", "true") mustBe BooleanFeatureSwitch("test", enabled = true)
    }

    "return ValueSetFeatureSwitch when supplied system-date and 2018-01-01" in {
      FeatureSwitch.setProperty("system-date", "2018-01-01T12:00:00Z") mustBe ValueSetFeatureSwitch("system-date", "2018-01-01T12:00:00Z")
    }
  }

  "enable" should {
    "set the value for the supplied key to 'true'" in {
      System.setProperty("feature.test", "false")

      FeatureSwitch.enable(booleanFeatureSwitch) mustBe BooleanFeatureSwitch("test", enabled = true)
    }
  }

  "disable" should {
    "set the value for the supplied key to 'false'" in {
      System.setProperty("feature.test", "true")

      FeatureSwitch.disable(booleanFeatureSwitch) mustBe BooleanFeatureSwitch("test", enabled = false)
    }
  }

  "dynamic toggling should be supported" in {
    FeatureSwitch.disable(booleanFeatureSwitch).enabled mustBe false
    FeatureSwitch.enable(booleanFeatureSwitch).enabled mustBe true
  }

  "apply" should {

    "return a constructed BooleanFeatureSwitch if the set system property is a boolean" in {
      System.setProperty("feature.test", "true")

      FeatureSwitch("test") mustBe BooleanFeatureSwitch("test", enabled = true)
    }

    "create an instance of BooleanFeatureSwitch which inherits FeatureSwitch" in {
      FeatureSwitch("test") mustBe a[FeatureSwitch]
      FeatureSwitch("test") mustBe a[BooleanFeatureSwitch]
    }

    "create an instance of EnabledTimedFeatureSwitch which inherits FeatureSwitch" in {
      System.setProperty("feature.test", "2016-05-05T14:30:00Z_2016-05-08T14:30:00Z")

      FeatureSwitch("test") mustBe a[FeatureSwitch]
      FeatureSwitch("test") mustBe a[TimedFeatureSwitch]
      FeatureSwitch("test") mustBe a[EnabledTimedFeatureSwitch]
    }

    "return an enabled EnabledTimedFeatureSwitch when only the end datetime is specified and is in the future" in {
      System.setProperty("feature.test", "X_9999-05-08T14:30:00Z")

      FeatureSwitch("test") mustBe a[EnabledTimedFeatureSwitch]
      FeatureSwitch("test").enabled mustBe true
    }

    "return a disabled EnabledTimedFeatureSwitch when only the end datetime is specified and is in the past" in {
      System.setProperty("feature.test", "X_2000-05-08T14:30:00Z")

      FeatureSwitch("test") mustBe a[EnabledTimedFeatureSwitch]
      FeatureSwitch("test").enabled mustBe false
    }

    "return an enabled EnabledTimedFeatureSwitch when only the start datetime is specified and is in the past" in {
      System.setProperty("feature.test", "2000-05-05T14:30:00Z_X")

      FeatureSwitch("test") mustBe a[EnabledTimedFeatureSwitch]
      FeatureSwitch("test").enabled mustBe true
    }

    "return a disabled TimedFeatureSwitch when neither date is specified" in {
      System.setProperty("feature.test", "X_X")

      FeatureSwitch("test").enabled mustBe false
    }

    "create an instance of DisabledTimedFeatureSwitch which inherits FeatureSwitch" in {
      System.setProperty("feature.test", "!2016-05-05T14:30:00Z_2016-05-08T14:30:00Z")

      FeatureSwitch("test") mustBe a[FeatureSwitch]
      FeatureSwitch("test") mustBe a[TimedFeatureSwitch]
      FeatureSwitch("test") mustBe a[DisabledTimedFeatureSwitch]
    }

    "return an enabled DisabledTimedFeatureSwitch when only the end datetime is specified and is in the future" in {
      System.setProperty("feature.test", "!X_9999-05-08T14:30:00Z")

      FeatureSwitch("test") mustBe a[DisabledTimedFeatureSwitch]
      FeatureSwitch("test").enabled mustBe false
    }

    "return a disabled DisabledTimedFeatureSwitch when only the end datetime is specified and is in the past" in {
      System.setProperty("feature.test", "!X_2000-05-08T14:30:00Z")

      FeatureSwitch("test") mustBe a[DisabledTimedFeatureSwitch]
      FeatureSwitch("test").enabled mustBe true
    }

    "return an enabled DisabledTimedFeatureSwitch when only the start datetime is specified and is in the past" in {
      System.setProperty("feature.test", "!2000-05-05T14:30:00Z_X")

      FeatureSwitch("test") mustBe a[DisabledTimedFeatureSwitch]
      FeatureSwitch("test").enabled mustBe false
    }

    "return an enabled DisabledTimedFeatureSwitch when neither date is specified" in {
      System.setProperty("feature.test", "!X_X")

      FeatureSwitch("test").enabled mustBe true
    }

    "return a ValueSetFeatureSwitch when the property is set to time-clear" in {
      System.setProperty("feature.test", "time-clear")

      FeatureSwitch("test") mustBe a[ValueSetFeatureSwitch]
      FeatureSwitch("test").enabled mustBe true
      FeatureSwitch("test").value mustBe "time-clear"
    }

    "return a ValueSetFeatureSwitch when the property is set to a date like 2018-09-23T00:00:00" in {
      System.setProperty("feature.test", "2018-09-23T00:00:00Z")

      FeatureSwitch("test") mustBe a[ValueSetFeatureSwitch]
      FeatureSwitch("test").enabled mustBe true
      FeatureSwitch("test").value mustBe "2018-09-23T00:00:00Z"
    }
  }

  "unapply" should {

    "deconstruct a given FeatureSwitch into it's name and a false enabled value if undefined as a system property" in {
      val fs = FeatureSwitch("test")

      FeatureSwitch.unapply(fs) mustBe Some("test" -> false)
    }

    "deconstruct a given FeatureSwitch into its name and true if defined as true as a system property" in {
      System.setProperty("feature.test", "true")
      val fs = FeatureSwitch("test")

      FeatureSwitch.unapply(fs) mustBe Some("test" -> true)
    }

    "deconstruct a given FeatureSwitch into its name and false if defined as false as a system property" in {
      System.setProperty("feature.test", "false")
      val fs = FeatureSwitch("test")

      FeatureSwitch.unapply(fs) mustBe Some("test" -> false)
    }

    "deconstruct a given TimedFeatureSwitch into its name and enabled flag if defined as a system property" in {
      System.setProperty("feature.test", "2016-05-05T14:30:00Z_2016-05-08T14:30:00Z")
      val fs = FeatureSwitch("test")

      FeatureSwitch.unapply(fs) mustBe Some("test" -> false)
    }

    "deconstruct a given ValueSetFeatureSwitch into its name and enabled flag if defined as a system property" in {
      System.setProperty("feature.test", "time-clear")
      val fs = FeatureSwitch("test")

      FeatureSwitch.unapply(fs) mustBe Some("test" -> true)
    }
  }

  "PAYEFeatureSwitches" should {
    "return a disabled feature when the associated system property doesn't exist" in {
      payeFeatureSwitch.desService.enabled mustBe false
    }

    "return an enabled feature when the associated system property is true" in {
      FeatureSwitch.enable(payeFeatureSwitch.desService)

      payeFeatureSwitch.desService.enabled mustBe true
    }

    "return a disable feature when the associated system property is false" in {
      FeatureSwitch.disable(payeFeatureSwitch.desService)

      payeFeatureSwitch.desService.enabled mustBe false
    }

    "return true if the desServiceFeature system property is true" in {
      System.setProperty("feature.desServiceFeature", "true")

      payeFeatureSwitch("desServiceFeature") mustBe Some(BooleanFeatureSwitch("desServiceFeature", true))
    }

    "return false if the desServiceFeature system property is false" in {
      System.setProperty("feature.desServiceFeature", "false")

      payeFeatureSwitch("desServiceFeature") mustBe Some(BooleanFeatureSwitch("desServiceFeature", false))
    }

    "return an empty option if a system property doesn't exist when using the apply function" in {
      payeFeatureSwitch("somethingElse") mustBe None
    }

    "return true if the removeStaleDocumentsFeature system property is true" in {
      System.setProperty("feature.removeStaleDocumentsFeature", "true")

      payeFeatureSwitch("removeStaleDocumentsFeature") mustBe Some(BooleanFeatureSwitch("removeStaleDocumentsFeature", true))
    }

    "return false if the removeStaleDocumentsFeature system property is false" in {
      System.setProperty("feature.removeStaleDocumentsFeature", "false")

      payeFeatureSwitch("removeStaleDocumentsFeature") mustBe Some(BooleanFeatureSwitch("removeStaleDocumentsFeature", false))
    }

    "return true if the system-date system property is time-clear" in {
      System.setProperty("feature.system-date", "time-clear")

      payeFeatureSwitch("system-date") mustBe Some(ValueSetFeatureSwitch("system-date", "time-clear"))
      payeFeatureSwitch("system-date").get.enabled mustBe true
    }

    "return true if the system-date system property is date like 2018-09-23T00:00:00" in {
      System.setProperty("feature.system-date", "2018-09-23T00:00:00Z")

      payeFeatureSwitch("system-date") mustBe Some(ValueSetFeatureSwitch("system-date", "2018-09-23T00:00:00Z"))
      payeFeatureSwitch("system-date").get.enabled mustBe true
    }
  }

  "TimedFeatureSwitch" should {

    val START = "2000-01-23T14:00:00.00Z"
    val END = "2000-01-23T15:30:00.00Z"
    val startDateTime = Some(LocalDateTime.parse(START, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    val endDatetime = Some(LocalDateTime.parse(END, DateTimeFormatter.ISO_OFFSET_DATE_TIME))

    "be enabled when within the specified time range" in {
      val now = LocalDateTime.parse("2000-01-23T14:30:00.00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME)

      EnabledTimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled mustBe true
      DisabledTimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled mustBe false
    }

    "be enabled when current time is equal to the start time" in {
      val now = LocalDateTime.parse(START, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

      EnabledTimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled mustBe true
      DisabledTimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled mustBe false
    }

    "be enabled when current time is equal to the end time" in {
      val now = LocalDateTime.parse(END, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

      EnabledTimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled mustBe true
      DisabledTimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled mustBe false
    }

    "be disabled when current time is outside the specified time range" in {
      val now = LocalDateTime.parse("1900-01-23T12:00:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME)

      EnabledTimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled mustBe false
      DisabledTimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled mustBe true
    }

    "be disabled when current time is in the future of the specified time range with an unspecified start" in {
      val now = LocalDateTime.parse("2100-01-23T12:00:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME)

      EnabledTimedFeatureSwitch("test", None, endDatetime, now).enabled mustBe false
      DisabledTimedFeatureSwitch("test", None, endDatetime, now).enabled mustBe true
    }

    "be enabled when current time is in the past of the specified time range with an unspecified start" in {
      val now = LocalDateTime.parse("1900-01-23T12:00:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME)

      EnabledTimedFeatureSwitch("test", None, endDatetime, now).enabled mustBe true
      DisabledTimedFeatureSwitch("test", None, endDatetime, now).enabled mustBe false
    }

    "be enabled when current time is in the range of the specified time range with an unspecified start" in {
      val now = LocalDateTime.parse("2000-01-23T14:30:00.00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME)

      EnabledTimedFeatureSwitch("test", None, endDatetime, now).enabled mustBe true
      DisabledTimedFeatureSwitch("test", None, endDatetime, now).enabled mustBe false
    }

    "be enabled when current time is in the future of the specified time range with an unspecified end" in {
      val now = LocalDateTime.parse("2100-01-23T12:00:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME)

      EnabledTimedFeatureSwitch("test", startDateTime, None, now).enabled mustBe true
      DisabledTimedFeatureSwitch("test", startDateTime, None, now).enabled mustBe false
    }

    "be disabled when current time is in the past of the specified time range with an unspecified end" in {
      val now = LocalDateTime.parse("1900-01-23T12:00:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME)

      EnabledTimedFeatureSwitch("test", startDateTime, None, now).enabled mustBe false
      DisabledTimedFeatureSwitch("test", startDateTime, None, now).enabled mustBe true
    }

    "be enabled when current time is in the range of the specified time range with an unspecified end" in {
      val now = LocalDateTime.parse("2000-01-23T14:30:00.00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME)

      EnabledTimedFeatureSwitch("test", None, endDatetime, now).enabled mustBe true
      DisabledTimedFeatureSwitch("test", None, endDatetime, now).enabled mustBe false
    }
  }

}
