package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.state.Var
import contentmanagement.model.vm.code.BeExpression
import interactionPlugins.blockEnvironment.config.*
import interactionPlugins.blockEnvironment.programming.BeProgram

case class TreeEditorState(
                            treeToEdit: Var[BeProgram],
                            controllerStateVar: Var[BeControllerState],
                            editorTreeDisplayConfig: Var[BeTreeDisplayConfig],
                            libraryTreeDisplayConfig: Var[BeTreeDisplayConfig],
                            rendererConfigVar: Var[BeRenderingConfig]
                          )

object TreeEditorState {

  def withInitExpression(initExpr: BeExpression): TreeEditorState = {
    val initEditorTreeDisplayConfig = BeTreeDisplayConfig(false, true, true, true)
    val initLibraryTreeDisplayConfig = BeTreeDisplayConfig(false, false, false, true)

    val initProgram = BeProgram(initExpr)
    val initRenderer = BeRenderingConfig.default()

    val initControllerState: BeControllerState = BeControllerState.default()

    TreeEditorState(Var(initProgram), Var(initControllerState), Var(initEditorTreeDisplayConfig), Var(initLibraryTreeDisplayConfig), Var(initRenderer))
  }


  def default(): TreeEditorState = {
    withInitExpression(BeProgram.miniProgramExpression())
  }

}