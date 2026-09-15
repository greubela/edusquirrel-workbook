package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.workbook.elements.interactionElements.programming.SnapTurtleCatalog.PaletteTab

/**
 * Composes turtle primitives with Snap control/operator/variable language blocks
 * for the Python-compatible editor palette. Turtle catalog stays embroidery/motion/pen;
 * control-flow selectors live in [[SnapControlFlow]].
 */
object SnapPaletteCatalog {

  val TabOrder: List[PaletteTab] = List(
    PaletteTab.Motion,
    PaletteTab.Pen,
    PaletteTab.Embroidery,
    PaletteTab.Control,
    PaletteTab.Operators,
    PaletteTab.Variables
  )

  val ControlLanguageSelectors: List[String] = List(
    "doRepeat",
    "doIf",
    "doIfElse",
    "doUntil",
    "doFor"
  )

  val OperatorSelectors: List[String] = List(
    "reportBoolean",
    "reportVariadicSum",
    "reportDifference",
    "reportVariadicProduct",
    "reportQuotient",
    "reportVariadicLessThan",
    "reportVariadicGreaterThan",
    "reportVariadicEquals",
    "reportVariadicLessThanOrEquals",
    "reportVariadicGreaterThanOrEquals",
    "reportVariadicNotEquals",
    "reportVariadicAnd",
    "reportVariadicOr",
    "reportNot"
  )

  val VariableSelectors: List[String] = List("doSetVar", "doChangeVar")

  def selectorsForTab(tab: PaletteTab): List[String] =
    tab match
      case PaletteTab.Motion | PaletteTab.Pen | PaletteTab.Embroidery =>
        SnapTurtleCatalog.primitivesForTab(tab).map(_.snapSelector)
      case PaletteTab.Control =>
        SnapTurtleCatalog.primitivesForTab(tab).map(_.snapSelector) ++ ControlLanguageSelectors
      case PaletteTab.Operators =>
        OperatorSelectors
      case PaletteTab.Variables =>
        VariableSelectors
      case PaletteTab.Other =>
        Nil

  def pythonCompatibleSelectors: List[String] =
    TabOrder.flatMap(selectorsForTab)

  def pythonCompatibleSelectorSet: Set[String] =
    pythonCompatibleSelectors.toSet
}
