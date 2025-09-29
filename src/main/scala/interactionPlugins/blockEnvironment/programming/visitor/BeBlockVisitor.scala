package interactionPlugins.blockEnvironment.programming.visitor

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.AppFont
import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.programming.BeBlock

import scala.collection.mutable

sealed trait BeBlockVisitor[S] {
  private val stateBuffer = mutable.ListBuffer[S](getInitState)

  protected def getInitState: S

  def currentState: S = allStates.last

  def allStates: List[S] = stateBuffer.toList

  def updateState(newState: S): Unit = stateBuffer.append(newState)
}


case class CodeBuilderVisitorState(current: String, curIntLevel: Int) {

  def appendInLine(str: String) = CodeBuilderVisitorState(current + str, curIntLevel)

  def appendParameters(pars: List[String]) = CodeBuilderVisitorState(current + pars.mkString("(", ",", ")"), curIntLevel)

  def appendNextLine(str: String): CodeBuilderVisitorState =
    CodeBuilderVisitorState(current + "\n" + ("    " * curIntLevel) + str, curIntLevel)

  def changeIntLevel(addLevel: Int): CodeBuilderVisitorState = CodeBuilderVisitorState(current, curIntLevel + addLevel)

}

case class ToPythonStringVisitor(importString: String) extends BeBlockVisitor[CodeBuilderVisitorState] {
  override  protected def getInitState: CodeBuilderVisitorState = CodeBuilderVisitorState(importString+"\n\n", 0)
}

//case class RenderState(boundsMap: Map[BeBlock, Bounds[Double]], svgData: Map[BeBlock, AppSvgElement])

case class SizeMapState(map: Map[BeBlock, Dimension[Double]]) {

  def add(block: BeBlock, dim: Dimension[Double]): SizeMapState = SizeMapState(map + (block -> dim))

}

case class CalculateSizeVisitor(font: AppFont, paddingSmall: Dimension[Double], paddingBig: Dimension[Double]) extends BeBlockVisitor[SizeMapState] {
  def getInitState: SizeMapState = SizeMapState(Map())
}

/*
case class SimulationVisitorState(
                                   svgElements: List[L.SvgElement]
                                 ) {
  // def addWithFactory(shapeFactory: (Double, Double, ) => L.SvgElement)
}

object SimulationVisitor extends BeBlockVisitor[SimulationVisitorState]
*/