package it.evadid.evacuation.eva1.control.traits.evamousevisualizer

import it.evadid.evacuation.core.datastructures.graphs.Position
import it.evadid.evacuation.core.graphic.model.EvaColor
import it.evadid.evacuation.eva1.control.traits.evamouselistener.{GraphObjectsSelector, GraphSpaceSelector}
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.{EvaEdge, RouterOrEdge}
import it.evadid.evacuation.eva1.model.evagraph.Router
import it.evadid.evacuation.shared.traits.graphic.EvaCanvas


object GenericGraphSelectorVisualizer {

  val yellowHighlightColor: EvaColor = EvaColor(254, 224, 139)


  def getAddEdgeVisualizer(): MouseListenerVisualizer[GraphObjectsSelector[Router]] = new MouseListenerVisualizer[GraphObjectsSelector[Router]] {
    override def visualizeMouseListener(mouseListener: GraphObjectsSelector[Router], canvas: EvaCanvas[_]): Unit = {
      val state = mouseListener.getState()

      if (state.curSelected.isEmpty && state.curMousePos.isDefined && state.curHighlightDest.nonEmpty) {
        genericHighlightLine(canvas, state.curHighlightDest.head.pos, state.curMousePos.get.pos, EvaColor.blue)
      }
      else if (state.curSelected.nonEmpty && state.curHighlightDest.nonEmpty) {
        genericHighlightLine(canvas, state.curSelected.head.pos, state.curHighlightDest.head.pos, EvaColor.green)
      }
      else if (state.curSelected.nonEmpty && state.curMousePos.isDefined) {
        val dest = state.curSelected.head.pos.pointBetween(state.curMousePos.get.pos, 0.5)
        genericHighlightLine(canvas, state.curSelected.head.pos, dest, EvaColor.darkGreen)
      }

      if (state.curSelected.nonEmpty) {
        genericRouterHighlight(canvas, state.curSelected.head)
      }
    }
  }

  def getObjectSelectorVisualizer(lineColor: EvaColor = EvaColor(254, 224, 139)): GraphObjectSelectorVisualizer[RouterOrEdge] = new GraphObjectSelectorVisualizer[RouterOrEdge] {
    override def drawHighlightLine(canvas: EvaCanvas[_], start: Position, end: Position): Unit = genericHighlightLine(canvas, start, end, lineColor)

    override def highlight(canvas: EvaCanvas[_], obj: RouterOrEdge): Unit = obj.getEither() match {
      case Left(router) => genericRouterHighlight(canvas, router, lineColor)
      case Right(edge) => genericEdgeHighlight(canvas, edge, lineColor)
    }
  }

  val getSpaceSelectorVisualizer: MouseListenerVisualizer[GraphSpaceSelector] = new MouseListenerVisualizer[GraphSpaceSelector]() {
    override def visualizeMouseListener(mouseListener: GraphSpaceSelector, canvas: EvaCanvas[_]): Unit = {
      val state = mouseListener.getState()
      if (state.curMousePos.isDefined) {

        if (state.curHighlightDest.isEmpty) {
          canvas.setColor(EvaColor(0, 128, 0))
        } else {
          canvas.setColor(EvaColor(255, 0, 0))
        }
        canvas.fillCircle(state.curMousePos.get.x, state.curMousePos.get.y, 20)

        canvas.setColor(EvaColor(255, 0, 0))
        state.curHighlightDest.foreach(conflictPosition => {
          canvas.drawLine(state.curMousePos.get.x, state.curMousePos.get.y, conflictPosition.x, conflictPosition.y, 2)
        })

      }
    }
  }

  def genericEdgeHighlight(canvas: EvaCanvas[_], edge: EvaEdge, color: EvaColor = yellowHighlightColor): Unit = {
    canvas.setColor(color)
    canvas.drawCircle(edge.pos.x, edge.pos.y, 20, 3)
  }

  def genericHighlightLine(canvas: EvaCanvas[_], start: Position, dest: Position, lineColor: EvaColor = EvaColor.black): Unit = {
    canvas.setColor(lineColor)
    canvas.drawLine(start.x, start.y, dest.x, dest.y, 2)
  }

  def genericRouterHighlight(canvas: EvaCanvas[_], obj: Router, color: EvaColor = yellowHighlightColor): Unit = {
    canvas.setColor(color)
    canvas.drawArc(obj.pos.x, obj.pos.y, 10, 180 + 45, 360 - 90, 3)
  }

}

