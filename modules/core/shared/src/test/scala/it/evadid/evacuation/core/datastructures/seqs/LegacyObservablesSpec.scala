package it.evadid.evacuation.core.datastructures.seqs

import it.evadid.evacuation.core.datastructures.utility.ObservableVar
import scala.collection.mutable.ListBuffer
import munit.FunSuite

class LegacyObservablesSpec extends FunSuite {
  test("sequence mutations preserve order, duplicates and prior snapshots") {
    val seq = new MutableObservableSeq[Int]
    assertEquals(seq += 1, List(1))
    val snapshot = seq += 2
    seq += 1
    assertEquals(seq -= 1, List(2, 1))
    assertEquals(snapshot, List(1, 2))
    assertEquals(seq.length, 2)
    assertEquals(seq(0), 2)
    assertEquals(seq, Seq(2, 1))
    assertEquals(seq.hashCode, Seq(2, 1).hashCode)
  }
  test("removing an absent value does not announce a removal") {
    val seq = new MutableObservableSeq[Int]
    val removed = ListBuffer.empty[Int]
    seq.addRemovedListener(n => removed += n)
    seq += 1
    seq -= 2
    assertEquals(removed.toList, Nil)
    seq -= 1
    assertEquals(removed.toList, List(1))
  }
  test("sequence listeners can be detached independently") {
    val seq = new MutableObservableSeq[Int]
    val added = ListBuffer.empty[Int]
    val removed = ListBuffer.empty[Int]
    val onAdd: Int => Any = n => added += n
    val onRemove: Int => Any = n => removed += n
    seq.addAddedListener(onAdd)
    seq.addRemovedListener(onRemove)
    seq += 1
    seq -= 1
    seq.removeAddedListener(onAdd)
    seq.removeRemovedListener(onRemove)
    seq += 2
    seq -= 2
    assertEquals(added.toList, List(1))
    assertEquals(removed.toList, List(1))
  }
  test("sequence iterators keep a consistent snapshot across later mutations") {
    val seq = new MutableObservableSeq[Int]
    seq += 1
    seq += 2
    val iterator = seq.iterator
    seq -= 2
    seq += 3
    assertEquals(iterator.toList, List(1, 2))
  }
  test("listeners removed during delivery do not skip another listener") {
    val seq = new MutableObservableSeq[Int]
    val seen = ListBuffer.empty[String]
    lazy val first: Int => Any = _ => { seen += "first"; seq.removeAddedListener(first) }
    seq.addAddedListener(first)
    seq.addAddedListener(_ => seen += "second")
    seq += 1
    seq += 2
    assertEquals(seen.toList, List("first", "second", "second"))
  }
  test("listeners added during delivery begin with the next change") {
    val seq = new MutableObservableSeq[Int]
    val seen = ListBuffer.empty[Int]
    seq.addAddedListener(n => if (n == 1) seq.addAddedListener(value => seen += value))
    seq += 1
    seq += 2
    assertEquals(seen.toList, List(2))
  }
  test("observable variables distinguish initialization from later changes") {
    val value = ObservableVar[String]()
    val initialized = ListBuffer.empty[String]
    val changed = ListBuffer.empty[(String, String)]
    value.addInitListener(s => initialized += s)
    value.addListener((old, next) => changed += ((old, next)))
    value.setValue("first")
    value.setValue("next")
    assertEquals(value.currentValue, "next")
    assertEquals(initialized.toList, List("first"))
    assertEquals(changed.toList, List(("first", "next")))
  }
  test("initialization listeners optionally replay and can be removed") {
    val value = ObservableVar("first")
    val seen = ListBuffer.empty[String]
    val listener: String => Any = s => seen += s
    value.addInitListener(listener)
    value.addInitListener(s => seen += "unexpected", false)
    value.removeInitListener(listener)
    value.setValue("next")
    assertEquals(seen.toList, List("first"))
  }
  test("observable variable listeners may detach themselves during delivery") {
    val value = ObservableVar("first")
    val seen = ListBuffer.empty[String]
    lazy val listener: (String, String) => Any = (_, _) => { seen += "first"; value.removeListener(listener) }
    value.addListener(listener)
    value.addListener((_, _) => seen += "second")
    value.setValue("next")
    value.setValue("last")
    assertEquals(seen.toList, List("first", "second", "second"))
  }
  test("null updates are rejected without changing the current value") {
    val value = ObservableVar("first")
    intercept[AssertionError](value.setValue(null))
    assertEquals(value.currentValue, "first")
  }
}
