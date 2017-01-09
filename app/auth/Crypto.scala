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

package auth

import play.api.libs.json.{JsString, Reads, Writes}
import uk.gov.hmrc.crypto.{CryptoWithKeysFromConfig, CompositeSymmetricCrypto, Crypted, PlainText}


trait Crypto {
  def crypto: CompositeSymmetricCrypto

  val rds: Reads[String] = Reads[String](js =>
    js.validate[String].map(encryptedUtr => {
      val crypted = Crypted.fromBase64(encryptedUtr)
      crypto.decrypt(crypted).value
    }
    )
  )

  val wts: Writes[String] = Writes[String](utr => {
    val crypted = crypto.encrypt(PlainText(utr))
    val encryptedUtr = new String(crypted.toBase64)
    JsString(encryptedUtr)
  }
  )
}

object Crypto extends Crypto {
  override val crypto = CryptoWithKeysFromConfig(baseConfigKey = "mongo-encryption")
}
