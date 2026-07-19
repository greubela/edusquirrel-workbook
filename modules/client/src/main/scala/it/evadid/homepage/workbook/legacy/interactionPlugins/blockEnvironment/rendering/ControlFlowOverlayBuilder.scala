package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering

import com.raquo.laminar.api.L
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.*
import todomove.webElementsOld.webElements.svg.shapes.composite.{BoxManualPositioning, ManualPositionElement}
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, BeShapeDecoration}

import scala.collection.mutable

case class ControlFlowOverlayBuilder(paths: List[ControlFlowPath], overlaysWithCenter: List[(BeShapeDecoration, Point[Double])], relativeOffsetInParent: Point[Double] = Point(0, 0)) {

  def firstOpenPath: ControlFlowPath = firstOpen._1

  def secondOpenPath: ControlFlowPath = secondOpen._1

  lazy val allOpenPaths: List[(ControlFlowPath, Int)] = paths.zipWithIndex.filter(_._1.curStatus == PathStatus.OPEN)
  private lazy val firstOpen: (ControlFlowPath, Int) = allOpenPaths(0)
  private lazy val secondOpen: (ControlFlowPath, Int) = allOpenPaths(1)

  private lazy val beforeFirst: List[ControlFlowPath] = paths.slice(0, firstOpen._2)
  private lazy val afterFirst: List[ControlFlowPath] = paths.slice(firstOpen._2 + 1, paths.size)
  private lazy val betweenFirstAndSecond: List[ControlFlowPath] = paths.slice(firstOpen._2 + 1, secondOpen._2)
  private lazy val afterSecond: List[ControlFlowPath] = paths.slice(secondOpen._2 + 1, paths.size)

  private lazy val lastPaused: (ControlFlowPath, Int) = paths.zipWithIndex.findLast(_._1.curStatus == PathStatus.PAUSED).get
  private lazy val beforeLastPaused: List[ControlFlowPath] = paths.slice(0, lastPaused._2)
  private lazy val afterLastPaused: List[ControlFlowPath] = paths.slice(lastPaused._2 + 1, paths.size)

  def startNewPathAtOrigin(isActive: Boolean, pathAmends: Seq[L.Modifier[L.SvgElement]]): ControlFlowOverlayBuilder = {
    val path = SvgPathBuilder[Double](relativeOffsetInParent) // todo correct start point!!

    val segment = PathSegment(path, List(), isActive, pathAmends)
    startNewPath(ControlFlowPath(PathStatus.OPEN, List(segment)))
  }

  def startNewPath(newPath: ControlFlowPath): ControlFlowOverlayBuilder = this.copy(paths = paths :+ newPath)

  def unionFirstTwoOpen(func: (ControlFlowPath, ControlFlowPath) => ControlFlowPath): ControlFlowOverlayBuilder = {
    val changed: ControlFlowPath = func(firstOpen._1, secondOpen._1)
    this.copy(paths = beforeFirst ++ List(changed) ++ betweenFirstAndSecond ++ afterSecond)
  }

  def firstOpenPathAddNewSegment(pathSegment: PathSegment): ControlFlowOverlayBuilder = {
    changeFirstOpenPath(toChange => toChange.copy(segments = toChange.segments :+ pathSegment))
  }

  def changeFirstOpenPath(func: ControlFlowPath => ControlFlowPath): ControlFlowOverlayBuilder = {
    val changed = func(firstOpen._1)
    this.copy(paths = beforeFirst ++ List(changed) ++ afterFirst)
  }

  def firstOpenPathChangeLastSegment(func: PathSegment => PathSegment): ControlFlowOverlayBuilder = {
    changeFirstOpenPath(toChange => toChange.changeLastPathSegment(seg => func(seg)))
  }

  def changeLastPaused(func: ControlFlowPath => ControlFlowPath): ControlFlowOverlayBuilder = {
    val changed = func(lastPaused._1)
    this.copy(paths = beforeLastPaused ++ List(changed) ++ afterLastPaused)
  }

  def resetHandledToOpen(): ControlFlowOverlayBuilder = this.copy(paths =
    paths.map(curPath => {
      if (curPath.curStatus == PathStatus.HANDLED) curPath.copy(curStatus = PathStatus.OPEN)
      else curPath
    })
  )

  def addDecoration(decoration: BeShapeDecoration, centeredAt: Point[Double]): ControlFlowOverlayBuilder = {
    this.copy(overlaysWithCenter = overlaysWithCenter :+ (decoration, centeredAt))
  }

  private def calcOffsetAndDimensionsForLines(config: BeRenderingConfig): List[ManualPositionElement] = {
    val allPathShapes = mutable.ListBuffer[BeShape]()
    for (curPath <- paths) {
      for (curSegment <- curPath.segments) {
        allPathShapes += SvgBridge.toFixedDimensionShape(curSegment.curPath).addAmends(curSegment.pathAmends)
      }
    }
    allPathShapes.toList.map(curPath => ManualPositionElement(curPath, Point[Double](0, 0), curPath.displaySize(config)))
  }

  private def calcOffsetAndDimensionsForOverlays(config: BeRenderingConfig): List[ManualPositionElement] = {

    overlaysWithCenter.map((curOverlay, curCenter) => {
      val shapeDim = curOverlay.displaySize(config)
      val topLeft: Point[Double] = curCenter.moveWithDimension(Dimension(shapeDim.width / -2.0, shapeDim.height / -2.0))
      ManualPositionElement(curOverlay, topLeft, shapeDim)
    })
  }


  def renderControlFlow(): BeShape = new BoxManualPositioning() {
    override def calcOffsetsAndDimensions(config: BeRenderingConfig): List[ManualPositionElement] = {
      calcOffsetAndDimensionsForLines(config) ++ calcOffsetAndDimensionsForOverlays(config)
    }
  }

}


object ControlFlowOverlayBuilder {

  enum PathStatus {
    case PAUSED, FINISHED, OPEN, HANDLED
  }

  case class PathSegment(curPath: SvgPathBuilder[Double], decorations: List[BeShapeDecoration], isActive: Boolean, pathAmends: Seq[L.Modifier[L.SvgElement]])

  case class ControlFlowPath(curStatus: PathStatus, segments: List[PathSegment]) {
    lazy val firstSegment: PathSegment = segments.head
    lazy val lastSegment: PathSegment = segments.last

    def continueWithNewSegment(newIsActive: Boolean, amends: Seq[L.Modifier[L.SvgElement]]): ControlFlowPath = {
      val newPathBuilder = SvgPathBuilder(lastSegment.curPath.current)
      val segment = PathSegment(newPathBuilder, List(), newIsActive, amends)
      ControlFlowPath(curStatus, segments :+ segment)
    }

    def changeLastPathBuilder(func: SvgPathBuilder[Double] => SvgPathBuilder[Double]): ControlFlowPath = {
      if (segments.isEmpty) throw new RuntimeException(s"ControlFlowPath::changeLastPathBuilder, tried to change last segment of empty path: $this")
      ControlFlowPath(PathStatus.HANDLED, segments.init :+ lastSegment.copy(curPath = func(lastSegment.curPath)))
    }

    def changeLastPathSegment(func: PathSegment => PathSegment): ControlFlowPath = {
      if (segments.isEmpty) throw new RuntimeException(s"ControlFlowPath::changeLastPathSegment, tried to change last segment of empty path: $this")
      ControlFlowPath(PathStatus.HANDLED, segments.init ++ List(func(lastSegment)))
    }

    def appendNewSegmentFromScratch(newSegment: PathSegment): ControlFlowPath = {
      ControlFlowPath(PathStatus.HANDLED, segments :+ newSegment)
    }

    def appendNewSegmentWithLastSegmentPosition(func: Point[Double] => PathSegment): ControlFlowPath = {
      appendNewSegmentFromScratch(func(lastSegment.curPath.current))
    }


  }

}