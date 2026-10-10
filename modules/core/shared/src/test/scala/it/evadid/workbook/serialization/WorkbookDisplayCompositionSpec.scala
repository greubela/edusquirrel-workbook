package it.evadid.workbook.serialization

import it.evadid.workbook.elements.displayElements.TwoColumnPanel
import it.evadid.workbook.elements.structureElements.ExerciseGroup
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import munit.FunSuite

class WorkbookDisplayCompositionSpec extends FunSuite {
  test("nested display compositions preserve child order and discover interactions") {
    val left = TextInteraction("left")
    val right = TextInteraction("right")
    val panel = TwoColumnPanel("columns", left, right)
    val group = ExerciseGroup("group", List(panel))
    assertEquals(panel.childrenOfThisElement, List(left, right))
    assertEquals(group.allChildrenFullSubtree, List(panel, left, right))
    assertEquals(group.allContainedInteractions, List(left, right))
    assertEquals(TwoColumnPanel.factory.idsRequiredForDeserialization(panel.toSerialized), Set("left", "right"))
    assertEquals(ExerciseGroup.factory.idsRequiredForDeserialization(group.toSerialized), Set("columns"))
  }

  test("an empty exercise group has no children or serialization dependencies") {
    val group = ExerciseGroup("empty", Nil)
    assertEquals(group.allChildrenFullSubtree, Nil)
    assertEquals(group.allContainedInteractions, Nil)
    assertEquals(ExerciseGroup.factory.idsRequiredForDeserialization(group.toSerialized), Set.empty[String])
  }
}
