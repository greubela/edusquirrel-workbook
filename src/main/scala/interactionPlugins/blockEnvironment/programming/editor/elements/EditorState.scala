package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.state.Var
import contentmanagement.model.vm.code.BeExpression
import interactionPlugins.blockEnvironment.config.*
import interactionPlugins.blockEnvironment.programming.BeProgram

case class EditorState(
                            treeToEdit: Var[BeProgram],
                            controllerStateVar: Var[BeControllerState],
                            editorTreeDisplayConfig: Var[BeTreeDisplayConfig],
                            libraryTreeDisplayConfig: Var[BeTreeDisplayConfig],
                            rendererConfigVar: Var[BeRenderingConfig]
                          )

object EditorState {

  def withInitExpression(initExpr: BeExpression): EditorState = {
    val initEditorTreeDisplayConfig = BeTreeDisplayConfig(false, true, true, true)
    val initLibraryTreeDisplayConfig = BeTreeDisplayConfig(false, false, false, true)

    val initProgram = BeProgram(initExpr)
    val initRenderer = BeRenderingConfig.default()

    val initControllerState: BeControllerState = BeControllerState.default()

    EditorState(Var(initProgram), Var(initControllerState), Var(initEditorTreeDisplayConfig), Var(initLibraryTreeDisplayConfig), Var(initRenderer))
  }


  def default(): EditorState = {
    withInitExpression(BeProgram.miniProgramExpression())
  }

}