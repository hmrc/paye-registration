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

package config

import auth.{CryptoSCRS, CryptoSCRSImpl}
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import jobs._
import repositories.{IICounterMongo, RegistrationMongo, SequenceMongo}
import services.{MetricsService, MetricsSrv, RemoveStaleDocsService, RemoveStaleDocsServiceImpl}



class Module extends AbstractModule {

  override def configure(): Unit = {

    bind(classOf[RegistrationMongo]).asEagerSingleton()
    bind(classOf[IICounterMongo]).asEagerSingleton()
    bind(classOf[SequenceMongo]).asEagerSingleton()

    bind(classOf[LockRepositoryProvider]).to(classOf[LockRepositoryProviderImpl]).asEagerSingleton()
    bind(classOf[CryptoSCRS]).to(classOf[CryptoSCRSImpl]).asEagerSingleton()
    bind(classOf[StartUpJobs]).to(classOf[StartUpJobsImpl]).asEagerSingleton()

    //service
    bind(classOf[MetricsSrv]).to(classOf[MetricsService])
    bind(classOf[RemoveStaleDocsService]).to(classOf[RemoveStaleDocsServiceImpl]).asEagerSingleton()

    // jobs
    bind(classOf[ScheduledJob]).annotatedWith(Names.named("remove-stale-documents-job")).to(classOf[RemoveStaleDocumentsJobs]).asEagerSingleton()
    bind(classOf[ScheduledJob]).annotatedWith(Names.named("metrics-job")).to(classOf[MetricsJobs]).asEagerSingleton()
  }
}
