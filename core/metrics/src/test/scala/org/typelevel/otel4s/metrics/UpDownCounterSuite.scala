/*
 * Copyright 2022 Typelevel
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

package org.typelevel.otel4s
package metrics

import cats.effect.IO
import cats.effect.Ref
import munit.CatsEffectSuite
import org.typelevel.otel4s.meta.InstrumentMeta

class UpDownCounterSuite extends CatsEffectSuite {
  import UpDownCounterSuite._

  test("do not allocate attributes when instrument is noop") {
    val counter = UpDownCounter.noop[IO, Long]

    var allocated = false

    def allocateAttribute = {
      allocated = true
      List(Attribute("key", "value"))
    }

    for {
      _ <- counter.add(1L, allocateAttribute: _*)
      _ <- counter.inc(allocateAttribute: _*)
      _ <- counter.dec(allocateAttribute: _*)
    } yield assert(!allocated)
  }

  test("record value and attributes") {
    val attribute = Attribute("key", "value")

    val expected =
      List(
        Record(1L, Seq(attribute)),
        Record(2L, Nil),
        Record(3L, Seq(attribute, attribute))
      )

    for {
      counter <- inMemoryUpDownCounter
      _ <- counter.add(1L, attribute)
      _ <- counter.add(2L)
      _ <- counter.add(3L, attribute, attribute)
      records <- counter.records
    } yield assertEquals(records, expected)
  }

  test("inc by one") {
    val attribute = Attribute("key", "value")

    val expected =
      List(
        Record(1L, Seq(attribute)),
        Record(1L, Nil),
        Record(1L, Seq(attribute, attribute))
      )

    for {
      counter <- inMemoryUpDownCounter
      _ <- counter.inc(attribute)
      _ <- counter.inc()
      _ <- counter.inc(attribute, attribute)
      records <- counter.records
    } yield assertEquals(records, expected)
  }

  test("dec by one") {
    val attribute = Attribute("key", "value")

    val expected =
      List(
        Record(-1L, Seq(attribute)),
        Record(-1L, Nil),
        Record(-1L, Seq(attribute, attribute))
      )

    for {
      counter <- inMemoryUpDownCounter
      _ <- counter.dec(attribute)
      _ <- counter.dec()
      _ <- counter.dec(attribute, attribute)
      records <- counter.records
    } yield assertEquals(records, expected)
  }

  private def inMemoryUpDownCounter: IO[InMemoryUpDownCounter] =
    IO.ref[List[Record[Long]]](Nil).map(ref => new InMemoryUpDownCounter(ref))

}

object UpDownCounterSuite {

  final case class Record[A](value: A, attributes: Seq[Attribute[_]])

  class InMemoryUpDownCounter(ref: Ref[IO, List[Record[Long]]])
      extends UpDownCounter[IO, Long] {

    val backend: UpDownCounter.Backend[IO, Long] =
      new UpDownCounter.LongBackend[IO] {
        val meta: InstrumentMeta[IO] = InstrumentMeta.enabled

        def add(value: Long, attributes: Attribute[_]*): IO[Unit] =
          ref.update(_.appended(Record(value, attributes)))
      }

    def records: IO[List[Record[Long]]] =
      ref.get
  }

}
