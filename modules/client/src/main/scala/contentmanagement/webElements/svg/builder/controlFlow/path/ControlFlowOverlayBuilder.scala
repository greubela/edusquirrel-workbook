package contentmanagement.webElements.svg.builder.controlFlow.path

import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.builder.controlFlow.*
import contentmanagement.webElements.svg.shapes.composite.*
import contentmanagement.webElements.svg.shapes.*
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.core.datastructures.geometry.{Dimension, Point}

import scala.collection.mutable

case class ControlFlowPathOverlay(pathStack: List[ControlFlowPath], overlaysWithCenter: List[(BeShapeDecoration, Point[Double])]) extends ControlFlowOverlayElement {

  def lastPathByType(pathType: PathType): Option[(ControlFlowPath, Int)] = {
    pathStack.zipWithIndex.findLast(pathWithIndex => pathWithIndex._1.pathType == pathType)
  }

  def lastPathByStatusAndType(status: PathStatus, pathType: PathType): Option[(ControlFlowPath, Int)] = {
    pathStack.zipWithIndex.findLast(pathWithIndex => pathWithIndex._1.curStatus == status && pathWithIndex._1.pathType == pathType)
  }

  def startNewPath(position: Point[Double], pathType: PathType, segmentType: SegmentType): ControlFlowPathOverlay = {
    this.copy(pathStack = pathStack :+ ControlFlowPath(PathStatus.OPEN, pathType, List(ControlFlowPathSegment(SvgPathBuilder(position), segmentType))))
  }

  def addDecoration(decoration: BeShapeDecoration, centeredAt: Point[Double]): ControlFlowPathOverlay = {
    this.copy(overlaysWithCenter = overlaysWithCenter :+ (decoration, centeredAt))
  }

  def addPath(newPath: ControlFlowPath): ControlFlowPathOverlay = {
    this.copy(pathStack = pathStack :+ newPath)
  }

  def setPathStatus(pathType: PathType, status: PathStatus): ControlFlowPathOverlay = {
    lastPathByType(pathType)
      .map(tup => this.copy(pathStack = pathStack.updated(tup._2, tup._1.copy(curStatus = status))))
      .getOrElse(this)
  }

  def resetHandledToOpen(): ControlFlowPathOverlay = {
    this.copy(pathStack = pathStack.map(curPath => {
      if (curPath.curStatus == PathStatus.HANDLED) curPath.copy(curStatus = PathStatus.OPEN) else curPath
    }))
  }

  def changePathByStatusAndType(status: PathStatus, pathType: PathType, setToHandled: Boolean = true)(func: ControlFlowPath => ControlFlowPath): ControlFlowPathOverlay = {
    lastPathByStatusAndType(status, pathType).map(tup => replacePath(tup._2, func(tup._1), setToHandled)).getOrElse(this)
  }

  def changePathLastSegmentByStatusAndType(status: PathStatus, pathType: PathType, setToHandled: Boolean = true)(func: ControlFlowPathSegment => ControlFlowPathSegment): ControlFlowPathOverlay = {
    changePathByStatusAndType(status, pathType, setToHandled)(path => {
      if (path.segments.isEmpty) {
        throw new RuntimeException(s"ControlFlowPathOverlay::changePathLastSegmentByStatusAndType, tried to change last segment of empty path: $path")
      }
      path.copy(segments = path.segments.init :+ func(path.segments.last))
    })
  }

  def changePathBuilderByStatusAndType(status: PathStatus, pathType: PathType, setToHandled: Boolean = true)(func: SvgPathBuilder[Double] => SvgPathBuilder[Double]): ControlFlowPathOverlay = {
    changePathLastSegmentByStatusAndType(status, pathType, setToHandled)(segment => segment.copy(curPath = func(segment.curPath)))
  }

  def appendSegmentByStatusAndType(status: PathStatus, pathType: PathType, newSegmentType: SegmentType, setToHandled: Boolean = true)(func: Point[Double] => SvgPathBuilder[Double]): ControlFlowPathOverlay = {
    changePathByStatusAndType(status, pathType, setToHandled)(path => {
      if (path.segments.isEmpty) {
        throw new RuntimeException(s"ControlFlowPathOverlay::appendSegmentByStatusAndType, tried to append segment to empty path: $path")
      }
      val newPathBuilder = func(path.segments.last.curPath.current)
      path.copy(segments = path.segments :+ ControlFlowPathSegment(newPathBuilder, newSegmentType))
    })
  }

  def unionOpenPathsByType(firstType: PathType, secondType: PathType, setToHandled: Boolean = true)(func: (ControlFlowPath, ControlFlowPath) => ControlFlowPath): ControlFlowPathOverlay = {
    (lastPathByStatusAndType(PathStatus.OPEN, firstType), lastPathByStatusAndType(PathStatus.OPEN, secondType)) match {
      case (Some((firstPath, firstIndex)), Some((secondPath, secondIndex))) =>
        val merged = if (setToHandled) func(firstPath, secondPath).copy(curStatus = PathStatus.HANDLED) else func(firstPath, secondPath)
        val (keepIndex, dropIndex) = if (firstIndex < secondIndex) (firstIndex, secondIndex) else (secondIndex, firstIndex)
        val mergedStack = pathStack.updated(keepIndex, merged).patch(dropIndex, Nil, 1)
        this.copy(pathStack = mergedStack)
      case _ => this
    }
  }

  def replacePath(pathNr: Int, newPath: ControlFlowPath, setToHandled: Boolean): ControlFlowPathOverlay = {
    val resPath = if (setToHandled) newPath.copy(curStatus = PathStatus.HANDLED) else newPath
    this.copy(pathStack = pathStack.updated(pathNr, resPath))
  }

  def replaceSegment(pathNr: Int, segmentNr: Int, newSegment: ControlFlowPathSegment, setToHandled: Boolean): ControlFlowPathOverlay = {
    val changePath = pathStack(pathNr)
    val newPath = changePath.copy(segments = changePath.segments.updated(segmentNr, newSegment))
    replacePath(pathNr, newPath, setToHandled)
    val newStack = pathStack.updated(pathNr, newPath)
    this.copy(pathStack = newStack)
  }

  def changeSegment(pathNr: Int, segmentNr: Int, func: SvgPathBuilder[Double] => SvgPathBuilder[Double], setToHandled: Boolean): ControlFlowPathOverlay = {
    val segment = pathStack(pathNr).segments(segmentNr)
    replaceSegment(pathNr, segmentNr, segment.copy(curPath = func(segment.curPath)), setToHandled)
  }

  def addNewSegment(pathNr: Int, newSegment: ControlFlowPathSegment, setToHandled: Boolean = false): ControlFlowPathOverlay = {
    val path = pathStack(pathNr)
    val resPath = path.copy(segments = path.segments :+ newSegment)
    replacePath(pathNr, resPath, setToHandled)
  }

  private lazy val asSegmentList: List[(Int, ControlFlowPath, Int, ControlFlowPathSegment)] = pathStack.zipWithIndex.flatMap(tup1 => {
    tup1._1.segments.zipWithIndex.map(tup2 => (tup1._2, tup1._1, tup2._2, tup2._1))
  })

  def lastSegmentByStatus(status: PathStatus): Option[(Int, ControlFlowPath, Int, ControlFlowPathSegment)] = {
    asSegmentList.filter(_._2.curStatus == status).lastOption
  }

  def lastSegmentByStatusAndType(status: PathStatus, segmentType: SegmentType): Option[(Int, ControlFlowPath, Int, ControlFlowPathSegment)] = {
    asSegmentList.filter(_._2.curStatus == status).filter(_._4.segmentType == segmentType).lastOption
  }

  def continueLastSegmentOfTypeWithNewSegment(status: PathStatus, oldSegmentType: SegmentType, newSegmentType: SegmentType, setToHandled: Boolean = false): ControlFlowPathOverlay = {
    val res = lastSegmentByStatusAndType(status, oldSegmentType)
    val newPath = res.map(tup => {
      val oldEndPoint = tup._4.curPath.current
      val newSegment = ControlFlowPathSegment(SvgPathBuilder(oldEndPoint), newSegmentType)
      tup._2.copy(segments = tup._2.segments ++ List(newSegment))
    })
    if (res.nonEmpty && newPath.nonEmpty) replacePath(res.get._1, newPath.get, setToHandled)
    else this
  }

  def continueLastSegmentOfType(status: PathStatus, segmentType: SegmentType)(func: SvgPathBuilder[Double] => SvgPathBuilder[Double], setToHandled: Boolean = true): ControlFlowPathOverlay = {
    val last = lastSegmentByStatusAndType(status, segmentType)
    if (last.isEmpty) {
      println("[ERROR] ControlFlowPathBuilder::continueLastSegmentOfType, tried to continue segment of non-existing type: " + segmentType + " or status: " + status + ", stack: " + pathStack)
      this
    } else {
      changeSegment(last.get._1, last.get._3, func, setToHandled)
    }
  }

  def continueLastSegment(status: PathStatus)(func: SvgPathBuilder[Double] => SvgPathBuilder[Double], setToHandled: Boolean = true): ControlFlowPathOverlay = {
    val last = lastSegmentByStatus(status)
    if (last.isEmpty) {
      println("[ERROR] ControlFlowPathBuilder::continueLastSegment, tried to continue non-existing segment: " + status + ", stack: " + pathStack)
      this
    } else {
      changeSegment(last.get._1, last.get._3, func, setToHandled)
    }
  }

  private def calcOffsetAndDimensionsForLines(config: BeRenderingConfig): List[ManualPositionElement] = {
    val allPathShapes = mutable.ListBuffer[BeShape]()
    for (curPath <- pathStack) {
      for (curSegment <- curPath.segments) {
        val shape = curSegment.curPath.toFixedDimensionShape
        val amended = shape.addAmends(config.controlFlowAmendMap(curSegment.segmentType))
        allPathShapes += amended
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

  def toShape(renderingConfig: BeRenderingConfig): BeShape = new BoxManualPositioning() {
    override def calcOffsetsAndDimensions(config: BeRenderingConfig): List[ManualPositionElement] = {
      calcOffsetAndDimensionsForLines(config) ++ calcOffsetAndDimensionsForOverlays(config)
    }
  }

}

object ControlFlowOverlayBuilder {











}
