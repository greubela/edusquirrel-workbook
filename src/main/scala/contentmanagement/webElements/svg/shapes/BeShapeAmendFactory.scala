package contentmanagement.webElements.svg.shapes

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Signal, eventPropToProcessor, svg}
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram
import org.scalajs.dom.MouseEvent

case class BeShapeAmendFactory(rendererConfig: BeRenderingConfig) {

  def muteOnTreeDragged(programOfBlock: BeProgram, signal: Signal[BeControllerState], regularColors: Seq[L.Modifier[L.SvgElement]]): Seq[Signal[L.Modifier[L.SvgElement]]] = {
    signalBasedAmendChooser(signal.map(ev => ev.draggingEvent.nonEmpty && ev.draggingEvent.get.draggedProgram != programOfBlock), mutedColorsAmend, regularColors)
  }

  def signalBasedAmendChooser(firstOne: Signal[Boolean], firstAmends: Seq[L.Modifier[L.SvgElement]], secondAmends: Seq[L.Modifier[L.SvgElement]]): Seq[Signal[L.Modifier[L.SvgElement]]] = {
    firstAmends.zip(secondAmends).map { (first, second) => firstOne.signal.map(if (_) first else second) }
  }

  def defaultTextAmends: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.grayscale(0).toWebStyleString,
    svg.stroke := rendererConfig.colorPalette.grayscale(0).toWebStyleString
  )

  def darkVariableColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.stroke := rendererConfig.colorPalette.grayscale(3).toWebStyleString,
    svg.fill := rendererConfig.colorPalette.grayscale(4).toWebStyleString,
  )

  def lightVariableColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.stroke := rendererConfig.colorPalette.grayscale(1).toWebStyleString,
    svg.fill := rendererConfig.colorPalette.grayscale(4).toWebStyleString,
  )

  def mutedColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.grayscale(4).toWebStyleString,
    svg.stroke := rendererConfig.colorPalette.grayscale(4).toWebStyleString
  )

  def mutedColorsFunctionAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.yellows(4).toWebStyleString,
    svg.stroke := rendererConfig.colorPalette.yellows(4).toWebStyleString
  )

  def errorColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.reds(3).toWebStyleString,
    svg.stroke := rendererConfig.colorPalette.reds(1).toWebStyleString
  )

  def acceptingColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.greens(1).toWebStyleString,
    svg.stroke := rendererConfig.colorPalette.greens(1).toWebStyleString
  )

  def literalColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := "white",
    svg.stroke := rendererConfig.colorPalette.grayscale(1).toWebStyleString
  )

  def defaultFunctionColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.yellows(3).toWebStyleString,
    svg.stroke := rendererConfig.colorPalette.yellows(3).toWebStyleString,
  )

  def defaultControlColors: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.yellows(2).toWebStyleString,
    svg.stroke := rendererConfig.colorPalette.yellows(2).toWebStyleString,
  )

  def defaultControlFlowBackgroundAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.yellows(1).toWebStyleString,
    svg.stroke := rendererConfig.colorPalette.yellows(0).toWebStyleString,
  )

  def onMouseEnterAmend(handler: MouseEvent => Any): Seq[L.Modifier[L.SvgElement]] = List(
    L.onMouseEnter --> { e => handler(e) }
  )

  def onMouseLeaveAmend(handler: MouseEvent => Any): Seq[L.Modifier[L.SvgElement]] = List(
    L.onMouseEnter --> { e => handler(e) }
  )


  def splitSymbolControlFlowAmends: Seq[L.Modifier[L.SvgElement]] = {
    val strokeW = rendererConfig.controlSegmentSize / 5.0
    List(
      svg.strokeWidth := strokeW + "px",
      svg.fill := rendererConfig.colorPalette.grayscale(4).toWebStyleString,
      svg.stroke := rendererConfig.colorPalette.grayscale(0).toWebStyleString,
    )
  }

  def combineSymbolControlFlowAmends: Seq[L.Modifier[L.SvgElement]] = {
    val strokeW = rendererConfig.controlSegmentSize / 5.0
    List(
      svg.strokeWidth := strokeW + "px",
      svg.fill := rendererConfig.colorPalette.grayscale(4).toWebStyleString,
      svg.stroke := rendererConfig.colorPalette.grayscale(0).toWebStyleString,
    )
  }

  def crossSymbolControlFlowAmends: Seq[L.Modifier[L.SvgElement]] = {
    val strokeW = rendererConfig.controlSegmentSize / 5.0
    List(
      svg.strokeWidth := strokeW + "px",
      svg.fill := rendererConfig.colorPalette.grayscale(4).toWebStyleString,
      svg.stroke := rendererConfig.colorPalette.grayscale(0).toWebStyleString,
    )
  }

  def decorationElements: Seq[L.Modifier[L.SvgElement]] = {
    List(
      svg.stroke := "transparent",
      svg.fill := rendererConfig.colorPalette.grayscale(0).toWebStyleString,
    )
  }

  def activeControlFlowAmends: Seq[L.Modifier[L.SvgElement]] = {
    val strokeW = rendererConfig.controlSegmentSize / 5.0
    List(
      svg.strokeWidth := strokeW + "px",
      svg.fill := "transparent",
      svg.stroke := rendererConfig.colorPalette.grayscale(0).toWebStyleString,
    )
  }

  def inactiveControlFlowAmends: Seq[L.Modifier[L.SvgElement]] = {
    val strokeW = rendererConfig.controlSegmentSize / 5.0
    List(
      svg.strokeDashArray := "1,1",
      svg.strokeWidth := strokeW + "px",
      svg.fill := "transparent",
      svg.stroke := rendererConfig.colorPalette.grayscale(3).toWebStyleString,
    )
  }

  def trueConditionControlFlowAmends: Seq[L.Modifier[L.SvgElement]] = {
    val strokeW = rendererConfig.controlSegmentSize / 5.0
    List(
      svg.strokeWidth := strokeW + "px",
      svg.fill := "transparent",
      svg.stroke := rendererConfig.colorPalette.greens(0).toWebStyleString,
    )
  }

  def falseConditionControlFlowAmends: Seq[L.Modifier[L.SvgElement]] = {
    val strokeW = rendererConfig.controlSegmentSize / 5.0
    List(
      svg.strokeWidth := strokeW + "px",
      svg.fill := "transparent",
      svg.stroke := rendererConfig.colorPalette.reds(0).toWebStyleString,
    )
  }


}
