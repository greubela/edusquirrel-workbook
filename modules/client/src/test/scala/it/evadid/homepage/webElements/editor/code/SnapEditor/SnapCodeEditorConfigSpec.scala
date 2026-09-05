package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.workbook.elements.interactionElements.programming.{SnapControlFlow, SnapTurtlePythonBridge}
import munit.FunSuite

class SnapCodeEditorConfigSpec extends FunSuite {

  private val allowedSelectors: Set[String] =
    SnapTurtlePythonBridge.Primitives.map(_.snapSelector).toSet ++
      SnapControlFlow.ControlSelectors ++
      SnapControlFlow.VariableSelectors.filter(_ != "reportGetVar") ++
      Set(
        "reportTrue",
        "reportFalse",
        "reportBoolean",
        "reportVariadicLessThan",
        "reportVariadicGreaterThan",
        "reportVariadicEquals",
        "reportVariadicAnd",
        "reportVariadicOr",
        "reportNot"
      )

  test("PythonCompatibleSnapCategories lists only Python-safe tabs in order") {
    val tabs = SnapCodeEditorConfig.PythonCompatibleSnapCategories
    assertEquals(tabs.map(_.name), List("Motion", "Pen", "Control", "Operators", "Variables"))
    assertEquals(tabs.map(_.color), List(
      SnapCategoryColor.Motion,
      SnapCategoryColor.Pen,
      SnapCategoryColor.Control,
      SnapCategoryColor.Operators,
      SnapCategoryColor.Variables
    ))
  }

  test("PythonCompatibleSnapCategories uses explicit block lists, not native categories") {
    val tabs = SnapCodeEditorConfig.PythonCompatibleSnapCategories
    assert(tabs.forall(!_.useNativeCategory))
    assert(tabs.forall(_.selectableElements.nonEmpty))
  }

  test("PythonCompatibleSnapCategories excludes unsupported native categories") {
    val tabNames = SnapCodeEditorConfig.PythonCompatibleSnapCategories.map(_.name).toSet
    assert(!tabNames.contains("Looks"))
    assert(!tabNames.contains("Sound"))
    assert(!tabNames.contains("Sensing"))
  }

  test("PythonCompatibleSnapCategories exposes only allow-listed block selectors") {
    val selectors = SnapCodeEditorConfig.pythonCompatibleBlockSelectors
    assert(selectors.subsetOf(allowedSelectors), clue = selectors.diff(allowedSelectors))
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
        "doWait",
        "doRepeat",
        "doIf",
        "doIfElse",
        "doUntil",
        "reportBoolean",
        "reportVariadicLessThan",
        "reportVariadicGreaterThan",
        "reportVariadicEquals",
        "reportVariadicAnd",
        "reportVariadicOr",
        "reportNot",
        "doSetVar",
        "doChangeVar"
      )
    )
  }

  test("PythonCompatibleTesting wires the filtered palette into the editor config") {
    val config = SnapCodeEditorConfig.PythonCompatibleTesting
    assertEquals(config.libraryTabs, SnapCodeEditorConfig.PythonCompatibleSnapCategories)
    assertEquals(config.parts, SnapCodeEditorConfig.Testing.parts)
  }

  test("Variables tab enables Snap variable controls") {
    val variablesTab = SnapCodeEditorConfig.PythonCompatibleSnapCategories.find(_.name == "Variables").get
    assert(variablesTab.includeVariableControls)
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
