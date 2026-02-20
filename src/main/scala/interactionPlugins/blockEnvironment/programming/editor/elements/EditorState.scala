package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Signal
import contentmanagement.model.vm.code.BeExpression
import interactionPlugins.blockEnvironment.config
import interactionPlugins.blockEnvironment.config.*
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeTreeDropTarget
import workbook.model.info.WorkbookInfo
import workbook.model.interaction.InteractionVariable

case class EditorState(
                        treeToEdit: Var[BeProgram],
                        controllerStateVar: Var[BeEditorControllerState],
                        rendererConfigVar: Var[BeRenderingConfig]
                      ) {

  val editorTreeDisplayConfig: Var[BeTreeDisplayConfig] = Var(BeTreeDisplayConfig.editorDefaults)
  val libraryTreeDisplayConfig: Var[BeTreeDisplayConfig] = Var(BeTreeDisplayConfig.libraryDefaults)

  val libaryTreeControllerConfig: Var[BeTreeControllerConfig] = Var(BeTreeControllerConfig.libraryTreeConfig(this))
  val editTreeControllerConfig: Var[BeTreeControllerConfig] = Var(BeTreeControllerConfig.editTreeConfig(this))

  private val mainTreeSignal: Signal[(L.HtmlElement, List[BeTreeDropTarget])] = HtmlBeTreeDisplay.forMainTree(this).treeRenderingSignal

  val legalDropTargetsInDistanceOrder: Signal[List[BeTreeDropTarget]] = {
    mainTreeSignal.map(_._2).combineWith(controllerStateVar).map((existingDropTargets, controllerState) => {
      if (controllerState.mouseDragOverProgram.isEmpty || controllerState.draggingEvent.isEmpty) {
        List()
      } else {
        val mousePos = controllerState.mouseDragOverProgram.get.position
        val draggedProgram = controllerState.draggingEvent.get.draggedProgram
        val legalTargets = existingDropTargets//existingDropTargets.filter(_.extensionPoint.extensionWillBeUsedAsType.canTakeValuesFrom(draggedProgram.fullProgram.possibleStaticTypes).possibleWithoutSyntaxErrors)
        // todo: re-introduce legality check
        val legalTargetsInOrder = legalTargets.sortBy(_.placeholderBounds.centerPoint.distanceToSquared(mousePos))
        //println("legal targets (#" + legalTargetsInOrder.size + "): " + legalTargetsInOrder)
        legalTargetsInOrder
      }
    })
  }

  /*val simulatedTreeToDisplay: Signal[Option[L.HtmlElement]] = legalDropTargetsInDistanceOrder.map(legalDropTargetsInOrder => {
    if (legalDropTargetsInOrder.isEmpty) {
      val res: Option[L.HtmlElement] = None
      res
    } else {
      val addedMap = Map(legalDropTargetsInOrder.head.extensionPoint -> controllerStateVar.now().draggingEvent.get.draggedProgram.fullProgram)
      val newTree = treeToEdit.now().withInsertions(editorTreeDisplayConfig.now(), addedMap)
      println("redrawing")
      Some(HtmlBeTreeDisplay.render(newTree, editorTreeDisplayConfig.now(), rendererConfigVar.now(), BeTreeControllerConfig.noOpConfig(), this)._1)
    }
  }) // todo: change to overlay?

  val treeToDisplaySignal: Signal[L.HtmlElement] = simulatedTreeToDisplay.combineWith(mainTreeSignal.map(_._1)).map((simulatedTree, mainTree) => {
    mainTree
  })*/

  val treeToDisplaySignal: Signal[L.HtmlElement] = mainTreeSignal.map(_._1)

  legalDropTargetsInDistanceOrder.combineWith(controllerStateVar.signal).foreach((legalDropTargetsInOrder, controller) => {

    if (controller.unhandledDropEvents.nonEmpty) {
      val droppedEvent = controller.unhandledDropEvents.get
      controllerStateVar.update(_.copy(unhandledDropEvents = None))
      val draggedEvent = controller.draggingEvent.get
      controllerStateVar.update(_.copy(draggingEvent = None))
      if (legalDropTargetsInOrder.nonEmpty) {
        val addedMap = Map(legalDropTargetsInOrder.head.extensionPoint -> draggedEvent.draggedProgram.fullProgram)
        treeToEdit.update(_.withInsertions(editorTreeDisplayConfig.now(), addedMap))
      }
    }
  })(new Owner() {})


  /*def handleDrop(point: Point[Double], onProgram: BeProgram): Unit = {
    if(controllerStateVar.now().draggingEvent.nonEmpty){
      val treeDragged = controllerStateVar.now().draggingEvent.get.draggedProgram
      controllerStateVar.update(oldState => oldState.copy(draggingEvent = None))
      if(legalDropTargetsInDistanceOrder.nonEmpty) {
        treeToEdit.update(_.withInsertions(editorTreeDisplayConfig.now(), Map(point -> treeDragged.fullProgram)))
      }
    }*/
  /*if(controllerStateVar.now.draggingEvent.nonEmpty){
    val treeDragged = controllerStateVar.now.draggingEvent.get.draggedProgram
  }
  editorState.controllerStateVar.update(oldState => oldState.copy(draggingEvent = None))
  editorState.treeToEdit.update(_.withInsertions(editorState.editorTreeDisplayConfig.now(), Map()))

}*/

}

object EditorState {


  def withInitExpression(initExpr: BeExpression): EditorState = {

    val initProgram = BeProgram(initExpr)
    val initRenderer = BeRenderingConfig.default()

    val initControllerState: BeEditorControllerState = BeEditorControllerState.default()

    EditorState(Var(initProgram), Var(initControllerState), Var(initRenderer))
  }


  def default(): EditorState = {
    withInitExpression(BeProgram.miniProgramExpression())
  }

}