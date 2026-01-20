package contentmanagement.webElements.svg.builder.controlFlow.path

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Signal
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.builder.controlFlow.*
import contentmanagement.webElements.svg.shapes.composite.*
import contentmanagement.webElements.svg.shapes.*
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

import scala.collection.mutable

case class ControlFlowPathOverlay(pathStack: List[ControlFlowPath], overlaysWithCenter: List[(BeShapeDecoration, Point[Double])]) extends ControlFlowOverlayElement {

  def startNewPath(position: Point[Double], pathType: PathType, segmentType: SegmentType): ControlFlowPathOverlay = {
    this.copy(pathStack = pathStack :+ ControlFlowPath(PathStatus.OPEN, pathType, List(ControlFlowPathSegment(SvgPathBuilder(position), segmentType))))
  }

  def addDecoration(decoration: BeShapeDecoration, centeredAt: Point[Double]): ControlFlowPathOverlay = {
    this.copy(overlaysWithCenter = overlaysWithCenter :+ (decoration, centeredAt))
  }

  def finishPath(pathNr: Int): ControlFlowPathOverlay = {
    val resPath: ControlFlowPath = pathStack(pathNr).copy(curStatus = PathStatus.FINISHED)
    this.copy(pathStack = pathStack.updated(pathNr, resPath))
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

  def toShape(renderingConfig: BeRenderingConfig): BeShape = new BoxManualPositioning() {
    override def calcOffsetsAndDimensions(config: BeRenderingConfig): List[ManualPositionElement] = {
      pathStack.map(curPath => ManualPositionElement(curPath.toShape(renderingConfig), Point[Double](0, 0), curPath.toShape(config).displaySize(config)))
    }
  }

}

object ControlFlowOverlayBuilder {











}