package it.evadid.homepage.webElements.editor.code.SnapEditor

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
        "doRepeat",
        "doIf",
        "doIfElse",
        "doUntil",
        "reportTrue",
        "reportFalse",
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

  test("BeginnerTurtleCategories lists Motion, Pen, Control only") {
    val tabs = SnapCodeEditorConfig.BeginnerTurtleCategories
    assertEquals(tabs.map(_.name), List("Motion", "Pen", "Control"))
    assertEquals(tabs.map(_.color), List(
      SnapCategoryColor.Motion,
      SnapCategoryColor.Pen,
      SnapCategoryColor.Control
    ))
    assert(tabs.forall(!_.useNativeCategory))
    assert(tabs.forall(!_.includeVariableControls))
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

  test("BeginnerTurtleTesting wires the filtered palette into the editor config") {
    val config = SnapCodeEditorConfig.BeginnerTurtleTesting
    assertEquals(config.libraryTabs, SnapCodeEditorConfig.BeginnerTurtleCategories)
    assertEquals(config.parts, SnapCodeEditorConfig.Testing.parts)
  }
}
