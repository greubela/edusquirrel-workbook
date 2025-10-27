package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.state.Var
import contentmanagement.model.vm.expressions.BeExpression
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram


case class TreeEditorState(treeToEdit: Var[BeProgram], controllerStateVar: Var[BeControllerState], displayConfigVar: Var[BeDisplayConfig], rendererConfigVar: Var[BeRenderingConfig])

object TreeEditorState {

  def withInitExpression(initExpr: BeExpression): TreeEditorState = {
    val initDisplayConfig = BeDisplayConfig.default()
    val initProgram = BeProgram.fromExpression(initDisplayConfig, initExpr)
    val initRenderer = BeRenderingConfig.default()

    withInitValues(initExpr, initDisplayConfig, initRenderer)
  }

  def withInitValues(initExpr: BeExpression, initDisplayConfig: BeDisplayConfig, rendererInit: BeRenderingConfig): TreeEditorState = {
    val initProgram = BeProgram.fromExpression(initDisplayConfig, initExpr)
    val initControllerState: BeControllerState = BeControllerState.default()

    TreeEditorState(Var(initProgram), Var(initControllerState), Var(initDisplayConfig), Var(rendererInit))
  }

  def default(): TreeEditorState = {
    withInitExpression(BeProgram.miniProgramExpression())
  }

}