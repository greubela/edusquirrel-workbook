package interactionPlugins.blockEnvironment.secondInteration

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import interactionPlugins.blockEnvironment.programming.rendering.{BeProgramRenderer, BeRendererConfig}
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

class HtmlBeWorkspace(
  workspaceState: BeWorkspaceState,
  rendererConfig: BeRendererConfig,
  interactionController: BeWorkspaceInteractionController
) extends HtmlWorkbookElement {

  private val workspaceView =
    div(
      cls := "be-workspace-surface",
      onDragOver --> (_.preventDefault()),
      onDrop --> { event =>
        event.preventDefault()
        interactionController.handleWorkspaceDrop()
      },
      child <-- workspaceState.programSignal.map { program =>
        val renderer = BeProgramRenderer(program, rendererConfig)
        renderer.render().getDomElement()
      }
    )

  override def getDomElement(): L.Element = workspaceView
}
