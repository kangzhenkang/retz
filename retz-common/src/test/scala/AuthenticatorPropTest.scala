/**
 *    Retz
 *    Copyright (C) 2016-2017 Nautilus Technologies, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
import io.github.retz.auth.{AuthHeader, HmacSHA256Authenticator, NoopAuthenticator}
import io.github.retz.cli.TimestampHelper
import org.junit.Test
import org.scalacheck.{Gen, Prop}
import org.scalatest.junit.JUnitSuite
import org.scalatest.prop.Checkers

class AuthenticatorPropTest extends JUnitSuite {

  val nonEmptyString = Gen.oneOf(Gen.alphaLowerStr, Gen.alphaNumStr, Gen.alphaUpperStr) suchThat (_.nonEmpty)
  val verb = Gen.oneOf("PUT", "GET", "DELETE", "POST", "PATCH")

  @Test
  def encodeDecode(): Unit = {
    Checkers.check(Prop.forAllNoShrink(nonEmptyString, nonEmptyString, nonEmptyString, verb, nonEmptyString) {
      (key: String, secret: String, path: String, verb: String, md5: String) => {
        val authenticator = new HmacSHA256Authenticator(key, secret)
        val timestamp = TimestampHelper.now()
        val signature = authenticator.signature(verb, md5, timestamp, path)
        val header = authenticator.header(verb, md5, timestamp, path)
        println(authenticator.getKey)
        println(header, authenticator)
        val optionalHeader = AuthHeader.parseHeaderValue(header.buildHeader())
        key.equals(optionalHeader.get().key) && signature.equals(optionalHeader.get().signature) &&
        authenticator.authenticate(verb, md5, timestamp, path, key, signature)
      }
    })
  }

  @Test
  def noop(): Unit = {
    Checkers.check(Prop.forAllNoShrink(nonEmptyString, nonEmptyString, nonEmptyString, verb, nonEmptyString) {
      (key: String, signature: String, path: String, verb: String, md5: String) => {
        val authenticator = new NoopAuthenticator(key)
        val date = TimestampHelper.now()
        authenticator.authenticate(verb, md5, date, path, key, signature)
      }
    })
  }

  //def testConcat(): Unit = {
  //  Checkers.check((a: List[Int], b: List[Int]) => a.size + b.size == (a ::: b).size)
  //}
}
