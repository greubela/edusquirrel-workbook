package it.evadid.core.datastructures.numbers

import munit.FunSuite

class NumberConstraintsSpec extends FunSuite {
  test("empty constraints leave both bounds unrestricted") {
    val value = ValueDependentConstraints.empty[String, Double]().getConstraint("input")
    assertEquals(value.minimum, None)
    assertEquals(value.maximum, None)
  }
  test("combined constraints select the strongest lower and upper bounds") {
    val empty = ValueDependentConstraints.empty[String, Double]()
    val combined = empty.addConstraint(NumberConstraintImpl(Some(1.0), Some(10.0)))
      .addConstraint(NumberConstraintImpl(Some(3.0), Some(8.0)))
    assertEquals(combined.getConstraint("input").minimum, Some(3.0))
    assertEquals(combined.getConstraint("input").maximum, Some(8.0))
    assertEquals(empty.getConstraint("input").minimum, None)
  }
  test("each dynamic constraint is evaluated once per snapshot") {
    var calls = 0
    val constraints = ValueDependentConstraints.empty[Int, Double]().addConstraint { n =>
      calls += 1
      NumberConstraintImpl(Some(n.toDouble), Some(n.toDouble + 2))
    }
    val snapshot = constraints.getConstraint(5)
    assertEquals(snapshot.minimum, Some(5.0))
    assertEquals(snapshot.maximum, Some(7.0))
    assertEquals(snapshot.minimum, Some(5.0))
    assertEquals(calls, 1)
    assertEquals(constraints.getConstraint(8).minimum, Some(8.0))
    assertEquals(calls, 2)
  }
  test("one-sided and contradictory constraints retain their actual limits") {
    val constraints = ValueDependentConstraints.empty[Unit, Double]()
      .addConstraint(NumberConstraintImpl(Some(10.0), None))
      .addConstraint(NumberConstraintImpl(None, Some(5.0)))
    val snapshot = constraints.getConstraint(())
    assertEquals(snapshot.minimum, Some(10.0))
    assertEquals(snapshot.maximum, Some(5.0))
  }
}
