package todomove.webElementsOld.webElements.svg.shapes

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Signal, eventPropToProcessor, svg}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.{BeEditorControllerState, BeRenderingConfig}
import it.evadid.vm.BeProgram
import org.scalajs.dom.MouseEvent

case class BeShapeAmendFactory(rendererConfig: BeRenderingConfig) {

  def muteOnTreeDragged(programOfBlock: BeProgram, signal: Signal[BeEditorControllerState], regularColors: Seq[L.Modifier[L.SvgElement]]): Seq[Signal[L.Modifier[L.SvgElement]]] = {
    signalBasedAmendChooser(signal.map(ev => ev.draggingEvent.nonEmpty && ev.draggingEvent.get.draggedProgram != programOfBlock), mutedColorsAmend, regularColors)
  }

  def signalBasedAmendChooser(firstOne: Signal[Boolean], firstAmends: Seq[L.Modifier[L.SvgElement]], secondAmends: Seq[L.Modifier[L.SvgElement]]): Seq[Signal[L.Modifier[L.SvgElement]]] = {
    firstAmends.zip(secondAmends).map { (first, second) => firstOne.signal.map(if (_) first else second) }
  }

  def defaultTextAmends: Seq[L.Modifier[L.SvgElement]] = List(
    //svg.fill := rendererConfig.colorPalette.grayscale(0).toWebColor.webStyleRgbString
    svg.stroke := rendererConfig.colorPalette.grayscale(0).toWebColor.webStyleRgbString
  )

  def invertedTextAmends: Seq[L.Modifier[L.SvgElement]] = List(
    //svg.fill := rendererConfig.colorPalette.grayscale(4).toWebColor.webStyleRgbString,
    svg.stroke := rendererConfig.colorPalette.grayscale(4).toWebColor.webStyleRgbString
  )

  def variableColorsDefAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.stroke := rendererConfig.colorPalette.grayscale(0).toWebColor.webStyleRgbString,
    svg.fill := rendererConfig.colorPalette.grayscale(1).toWebColor.webStyleRgbString,
  )

  def variableColorsUsedAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.stroke := rendererConfig.colorPalette.grayscale(3).toWebColor.webStyleRgbString,
    svg.fill := rendererConfig.colorPalette.grayscale(4).toWebColor.webStyleRgbString,
  )

  def mutedColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.grayscale(4).toWebColor.webStyleRgbString,
    svg.stroke := rendererConfig.colorPalette.grayscale(4).toWebColor.webStyleRgbString
  )

  def mutedColorsFunctionAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.yellows(4).toWebColor.webStyleRgbString,
    svg.stroke := rendererConfig.colorPalette.yellows(4).toWebColor.webStyleRgbString
  )

  def errorColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.reds(3).toWebColor.webStyleRgbString,
    svg.stroke := rendererConfig.colorPalette.reds(1).toWebColor.webStyleRgbString
  )

  def acceptedDestinationAmends: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.greens(0).toWebColor.webStyleRgbString,
    svg.stroke := rendererConfig.colorPalette.greens(0).toWebColor.webStyleRgbString
  )

  def acceptingColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.greens(4).toWebColor.webStyleRgbString,
    svg.stroke := rendererConfig.colorPalette.greens(4).toWebColor.webStyleRgbString
  )

  def literalColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := "white",
    svg.stroke := rendererConfig.colorPalette.grayscale(1).toWebColor.webStyleRgbString
  )

  def defaultFunctionColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.yellows(3).toWebColor.webStyleRgbString,
    svg.stroke := rendererConfig.colorPalette.yellows(2).toWebColor.webStyleRgbString,
  )

  def defaultControlColors: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.yellows(2).toWebColor.webStyleRgbString,
    svg.stroke := rendererConfig.colorPalette.yellows(0).toWebColor.webStyleRgbString,
  )

  def defaultControlFlowBackgroundAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.yellows(2).toWebColor.webStyleRgbString,
    svg.stroke := rendererConfig.colorPalette.yellows(0).toWebColor.webStyleRgbString,
  )

  def onMouseEnterAmend(handler: MouseEvent => Any): Seq[L.Modifier[L.SvgElement]] = List(
    L.onMouseEnter --> { e => handler(e) }
  )

  def onMouseLeaveAmend(handler: MouseEvent => Any): Seq[L.Modifier[L.SvgElement]] = List(
    L.onMouseLeave --> { e => handler(e) }
  )


  def splitSymbolControlFlowAmends: Seq[L.Modifier[L.SvgElement]] = {
    val strokeW = rendererConfig.controlSegmentSize / 5.0
    List(
      svg.strokeWidth := s"${strokeW}px",
      svg.fill := rendererConfig.colorPalette.grayscale(4).toWebColor.webStyleRgbString,
      svg.stroke := rendererConfig.colorPalette.grayscale(0).toWebColor.webStyleRgbString,
    )
  }

  def unionSymbolControlFlowAmends: Seq[L.Modifier[L.SvgElement]] = {
    val strokeW = rendererConfig.controlSegmentSize / 5.0
    List(
      svg.strokeWidth := s"${strokeW}px",
      svg.fill := rendererConfig.colorPalette.grayscale(4).toWebColor.webStyleRgbString,
      svg.stroke := rendererConfig.colorPalette.grayscale(0).toWebColor.webStyleRgbString,
    )
  }

  def crossSymbolControlFlowAmends: Seq[L.Modifier[L.SvgElement]] = {
    val strokeW = rendererConfig.controlSegmentSize / 5.0
    List(
      svg.strokeWidth := s"${strokeW}px",
      svg.fill := rendererConfig.colorPalette.grayscale(4).toWebColor.webStyleRgbString,
      svg.stroke := rendererConfig.colorPalette.grayscale(0).toWebColor.webStyleRgbString,
    )
  }

  def inActiveDecorationElements: Seq[L.Modifier[L.SvgElement]] = {
    List(
      svg.stroke := "transparent",
      svg.fill := rendererConfig.colorPalette.yellows(3).toWebColor.webStyleRgbString,
    )
  }

  def activeDecorationElements: Seq[L.Modifier[L.SvgElement]] = {
    List(
      svg.stroke := "transparent",
      svg.fill := rendererConfig.colorPalette.grayscale(0).toWebColor.webStyleRgbString,
    )
  }

  def activeControlFlowAmends: Seq[L.Modifier[L.SvgElement]] = {
    val strokeW = rendererConfig.controlSegmentSize / 5.0
    List(
      svg.strokeWidth := s"${strokeW}px",
      svg.fill := "transparent",
      svg.stroke := rendererConfig.colorPalette.grayscale(0).toWebColor.webStyleRgbString,
    )
  }

  def inactiveControlFlowAmends: Seq[L.Modifier[L.SvgElement]] = {
    val strokeW = rendererConfig.controlSegmentSize / 5.0
    List(
      svg.strokeDashArray := "1,1",
      svg.strokeWidth := s"${strokeW}px",
      svg.fill := "transparent",
      svg.stroke := rendererConfig.colorPalette.yellows(4).toWebColor.webStyleRgbString,
    )
  }
  def activeTrueConditionControlFlowAmends: Seq[L.Modifier[L.SvgElement]] = {
    val strokeW = rendererConfig.controlSegmentSize / 5.0
    List(
      svg.strokeWidth := s"${strokeW}px",
      svg.fill := "transparent",
      svg.stroke := rendererConfig.colorPalette.greens(0).toWebColor.webStyleRgbString,
    )
  }

  def inactiveTrueConditionControlFlowAmends: Seq[L.Modifier[L.SvgElement]] = {
    val strokeW = rendererConfig.controlSegmentSize / 5.0
    List(
      svg.strokeDashArray := "1,1",
      svg.strokeWidth := s"${strokeW}px",
      svg.fill := "transparent",
      svg.stroke := rendererConfig.colorPalette.greens(2).toWebColor.webStyleRgbString,
    )
  }

  def activeFalseConditionControlFlowAmends: Seq[L.Modifier[L.SvgElement]] = {
    val strokeW = rendererConfig.controlSegmentSize / 5.0
    List(
      svg.strokeWidth := s"${strokeW}px",
      svg.fill := "transparent",
      svg.stroke := rendererConfig.colorPalette.reds(0).toWebColor.webStyleRgbString,
    )
  }

  def inactiveFalseConditionControlFlowAmends: Seq[L.Modifier[L.SvgElement]] = {
    val strokeW = rendererConfig.controlSegmentSize / 5.0
    List(
      svg.strokeDashArray := "1,1",
      svg.strokeWidth := s"${strokeW}px",
      svg.fill := "transparent",
      svg.stroke := rendererConfig.colorPalette.reds(2).toWebColor.webStyleRgbString,
    )
  }



}
