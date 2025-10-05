package interactionPlugins.blockEnvironment.secondInteration

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.AppLanguage
import workbook.workbookHtmlElements.HtmlPlaintextInstructionElement
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

class HtmlBeBlockEnvironment(content: BeExerciseContent) extends HtmlWorkbookElement {

  private val dragContext = new BeDragContext
  private val workspaceState = new BeWorkspaceState(content.initialProgram)
  private val interactionController = new BeWorkspaceInteractionController(dragContext, workspaceState)

  private val palette = new HtmlBeBlockPalette(
    content.paletteModel,
    content.rendererConfig,
    dragContext,
    entry => workspaceState.insertPaletteEntry(entry)
  )

  private val workspace = new HtmlBeWorkspace(workspaceState, content.rendererConfig, interactionController)
  private val executor = new BeProgramExecutor(workspaceState)
  private val instructions = HtmlPlaintextInstructionElement(content.instructionMap)

  private val domElement =
    div(
      cls := "be-block-environment",
      div(
        cls := "be-column be-palette-column",
        h3("Blocks"),
        palette.getDomElement()
      ),
      div(
        cls := "be-column be-workspace-column",
        h3("Workspace"),
        workspace.getDomElement()
      ),
      div(
        cls := "be-column be-info-column",
        h3(content.titleMap.getOrElse(AppLanguage.English, "Instructions")),
        instructions.getDomElement(),
        h3("Program output"),
        executor.getDomElement()
      )
    )

  override def getDomElement(): L.Element = domElement
}
