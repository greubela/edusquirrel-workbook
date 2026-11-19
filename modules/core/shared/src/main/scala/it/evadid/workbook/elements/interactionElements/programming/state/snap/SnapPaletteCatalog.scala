package it.evadid.workbook.elements.interactionElements.programming.state.snap

import it.evadid.vm.conversion.snapexpression.SnapControlFlow

import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog.PaletteTab

/**
 * Composes turtle primitives with Snap control/operator/variable language blocks
 * for the Python-compatible editor palette. Turtle catalog stays embroidery/motion/pen;
 * control-flow selectors live in [[SnapControlFlow]].
 */
object SnapPaletteCatalog {

  val TabOrder: Seq[PaletteTab] = List(
    PaletteTab.Motion,
    PaletteTab.Pen,
    PaletteTab.Embroidery,
    PaletteTab.Control,
    PaletteTab.Operators,
    PaletteTab.Variables
  )

  val ControlLanguageSelectors: Seq[String] = SnapControlFlow.ControlSelectorOrder

  val OperatorSelectors: Seq[String] = SnapControlFlow.OperatorPaletteOrder

  val VariableSelectors: Seq[String] = SnapControlFlow.VariableCommandOrder

  def selectorsForTab(tab: PaletteTab): Seq[String] =
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

  def pythonCompatibleSelectors: Seq[String] =
    TabOrder.flatMap(selectorsForTab)

  def pythonCompatibleSelectorSet: Set[String] =
    pythonCompatibleSelectors.toSet
}
