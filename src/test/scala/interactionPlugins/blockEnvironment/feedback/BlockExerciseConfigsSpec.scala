package interactionPlugins.blockEnvironment.feedback

import munit.FunSuite

final class BlockExerciseConfigsSpec extends FunSuite {

  test("addTwoNumbers config should enable unit tests and define visible tests") {
    val cfg = BlockExerciseConfigs.addTwoNumbers
    assert(cfg.enableUnitTests, "Unit tests should be enabled for addTwoNumbers")
    assert(cfg.visibleTests.nonEmpty, "addTwoNumbers should define visible tests")
    assert(cfg.hiddenTests.nonEmpty, "addTwoNumbers should define at least one hidden test")
  }

  test("maxInList config should define hidden tests") {
    val cfg = BlockExerciseConfigs.maxInList
    assert(cfg.enableUnitTests, "Unit tests should be enabled for maxInList")
    assert(cfg.hiddenTests.nonEmpty, "maxInList should define at least one hidden test")
  }
}
