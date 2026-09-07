package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.workbook.elements.interactionElements.programming.SnapPaletteCatalog
import munit.FunSuite

class SnapCodeEditorConfigSpec extends FunSuite {

  test("PythonCompatibleSnapCategories lists only Python-safe tabs in order") {
    val tabs = SnapCodeEditorConfig.PythonCompatibleSnapCategories
    assertEquals(tabs.map(_.name), List("Motion", "Pen", "Embroidery", "Control", "Operators", "Variables"))
    assertEquals(tabs.map(_.color), List(
      SnapCategoryColor.Motion,
      SnapCategoryColor.Pen,
      SnapCategoryColor.Embroidery,
      SnapCategoryColor.Control,
      SnapCategoryColor.Operators,
      SnapCategoryColor.Variables
    ))
  }

  test("PythonCompatibleSnapCategories uses explicit block lists, not native categories") {
    val tabs = SnapCodeEditorConfig.PythonCompatibleSnapCategories
    assert(tabs.forall(!_.useNativeCategory))
    assert(tabs.forall(tab => tab.selectableElements.nonEmpty || tab.includeMakeBlockButton || tab.includeVariableControls))
  }

  test("PythonCompatibleSnapCategories excludes unsupported native categories") {
    val tabNames = SnapCodeEditorConfig.PythonCompatibleSnapCategories.map(_.name).toSet
    assert(!tabNames.contains("Looks"))
    assert(!tabNames.contains("Sound"))
    assert(!tabNames.contains("Sensing"))
  }

  test("PythonCompatibleSnapCategories is derived from SnapPaletteCatalog") {
    val tabs = SnapCodeEditorConfig.PythonCompatibleSnapCategories
    assertEquals(
      tabs.map(_.selectableElements.map(_.id)),
      SnapPaletteCatalog.TabOrder.map(SnapPaletteCatalog.selectorsForTab)
    )
    assertEquals(SnapCodeEditorConfig.pythonCompatibleBlockSelectors, SnapPaletteCatalog.pythonCompatibleSelectorSet)
    val selectors = SnapCodeEditorConfig.pythonCompatibleBlockSelectors
    assert(selectors.contains("turnLeft"))
    assert(selectors.contains("setColor"))
    assert(selectors.contains("runningStitch"))
    assert(selectors.contains("doFor"))
    assert(selectors.contains("reportVariadicSum"))
    assert(selectors.contains("circle"))
    assert(selectors.contains("home"))
    assert(selectors.contains("backward"))
  }

  test("PythonCompatibleTesting wires the filtered palette into the editor config") {
    val config = SnapCodeEditorConfig.PythonCompatibleTesting
    assertEquals(config.libraryTabs, SnapCodeEditorConfig.PythonCompatibleSnapCategories)
    assertEquals(config.parts, SnapCodeEditorConfig.Testing.parts)
  }

  test("EmbroideryTesting uses the python-compatible palette including stitches") {
    val config = SnapCodeEditorConfig.EmbroideryTesting
    assertEquals(config.libraryTabs, SnapCodeEditorConfig.PythonCompatibleSnapCategories)
    assert(SnapCodeEditorConfig.pythonCompatibleBlockSelectors.contains("runningStitch"))
  }

  test("Variables tab enables Snap variable controls and make-block") {
    val variablesTab = SnapCodeEditorConfig.PythonCompatibleSnapCategories.find(_.name == "Variables").get
    assert(variablesTab.includeVariableControls)
    assert(variablesTab.includeMakeBlockButton)
  }

  test("mixedTab builds a non-native tab with the given blocks") {
    val blocks = List(
      LibraryBlock("forward", "", BeExpression.pass),
      LibraryBlock("doRepeat", "", BeExpression.pass)
    )
    val tab = SnapCodeEditorConfig.mixedTab("blocks", "Blocks", blocks)
    assertEquals(tab.id, "blocks")
    assertEquals(tab.name, "Blocks")
    assertEquals(tab.selectableElements.map(_.id), List("forward", "doRepeat"))
    assertEquals(tab.color, SnapCategoryColor.Other)
    assert(!tab.useNativeCategory)
    assert(!tab.includeVariableControls)
  }

  test("flattenToMixedTab preserves order and ORs includeVariableControls") {
    val motion = SnapCodeEditorConfig.PythonCompatibleSnapCategories.find(_.name == "Motion").get
    val variables = SnapCodeEditorConfig.PythonCompatibleSnapCategories.find(_.name == "Variables").get
    val flat = SnapCodeEditorConfig.flattenToMixedTab("all", "All", List(motion, variables))
    assertEquals(
      flat.selectableElements.map(_.id),
      motion.selectableElements.map(_.id) ++ variables.selectableElements.map(_.id)
    )
    assert(flat.includeVariableControls)
    assert(flat.includeMakeBlockButton)
    assert(!flat.useNativeCategory)
  }

  test("BeginnerTurtleCategories is a single mixed Blocks tab") {
    val tabs = SnapCodeEditorConfig.BeginnerTurtleCategories
    assertEquals(tabs.map(_.name), List("Blocks"))
    assertEquals(tabs.map(_.id), List("blocks"))
    assertEquals(tabs.map(_.color), List(SnapCategoryColor.Other))
    assert(tabs.forall(!_.useNativeCategory))
    assert(tabs.forall(!_.includeVariableControls))
    assertEquals(
      tabs.head.selectableElements.map(_.id),
      List(
        "receiveGo",
        "doRepeat",
        "forward",
        "turn",
        "gotoXY",
        "setHeading",
        "clear",
        "up",
        "down"
      )
    )
  }

  test("BeginnerTurtleCategories exposes exactly the nine beginner selectors") {
    val selectors = SnapCodeEditorConfig.beginnerTurtleBlockSelectors
    assertEquals(
      selectors,
      Set(
        "forward",
        "turn",
        "gotoXY",
        "setHeading",
        "clear",
        "down",
        "up",
        "receiveGo",
        "doRepeat"
      )
    )
    assert(selectors.subsetOf(SnapCodeEditorConfig.pythonCompatibleBlockSelectors))
  }

  test("BeginnerTurtleTesting wires the mixed palette and hides category buttons") {
    val config = SnapCodeEditorConfig.BeginnerTurtleTesting
    assertEquals(config.libraryTabs, SnapCodeEditorConfig.BeginnerTurtleCategories)
    assertEquals(config.parts, SnapCodeEditorConfig.Testing.parts.copy(libraryCategories = false))
    assert(!config.parts.libraryCategories)
  }
}
