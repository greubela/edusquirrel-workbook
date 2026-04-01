package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.api.L.{render, unsafeWindowOwner}
import contentmanagement.webElements.genericHtmlElements.editor.{SimpleBooleanEditor, SimpleSelectorEditor}
import interactionPlugins.blockEnvironment.config.{BeTreeControllerConfig, BeTreeDisplayConfig, ControlFlowDisplay}
import org.scalajs.dom
import contentmanagement.webElements.HtmlAppElement
import datastructures.core.language.{HumanLanguage, LanguageMap}
import util.web.DownloadHelper

case class HtmlEditorConfigPanel(editorState: EditorState) extends HtmlAppElement {

  private def createDisplayConfigVar[T](
      getter: BeTreeDisplayConfig => T
  )(setter: (BeTreeDisplayConfig, T) => BeTreeDisplayConfig): Var[T] = {
    val derived = Var(getter(editorState.editorTreeDisplayConfig.now()))

    editorState.editorTreeDisplayConfig.signal.foreach { config =>
      val newValue = getter(config)
      if (derived.now() != newValue) {
        derived.set(newValue)
      }
    }(unsafeWindowOwner)

    derived.signal.foreach { newValue =>
      val currentConfig = editorState.editorTreeDisplayConfig.now()
      if (getter(currentConfig) != newValue) {
        editorState.editorTreeDisplayConfig.update(cfg => setter(cfg, newValue))
      }
    }(unsafeWindowOwner)

    derived
  }

  private def booleanDisplayConfigEditor(
      label: String,
      getter: BeTreeDisplayConfig => Boolean
  )(setter: (BeTreeDisplayConfig, Boolean) => BeTreeDisplayConfig): Element = {
    SimpleBooleanEditor(
      createDisplayConfigVar(getter)(setter),
      LanguageMap.universalMap[HumanLanguage](label)
    ).getDomElement()
  }

  private lazy val controlFlowSelector: Element = {
    val options = List(
      ControlFlowDisplay.ControlFlowHidden -> "Hide control flow",
      ControlFlowDisplay.ControlFlowBackgrounds -> "Show control backgrounds",
      ControlFlowDisplay.ControlFlowShownFull -> "Show full control flow"
    )
    val labels = options.map(option => LanguageMap.universalMap[HumanLanguage](option._2))
    SimpleSelectorEditor(
      createDisplayConfigVar(_.controlFlowDisplay)((cfg, value) => cfg.copy(controlFlowDisplay = value)),
      options.map(_._1),
      labels
    ).getDomElement()
  }

  private def downloadCurrentTreeAsSvg(): Unit = {
    val program = editorState.treeToEdit.now()
    val displayConfig = editorState.editorTreeDisplayConfig.now()
    val rendererConfig = editorState.rendererConfigVar.now()
    val (domElement, _) = HtmlBeTreeDisplay.render(
      program,
      displayConfig,
      rendererConfig,
      BeTreeControllerConfig.noOpConfig(),
      editorState
    )

    val tempContainer = dom.document.createElement("div")
    val rootNode = render(tempContainer, domElement)
    val svgElement = Option(tempContainer.querySelector("svg"))
    val svgContent = svgElement.map(_.outerHTML).getOrElse(tempContainer.innerHTML)
    rootNode.unmount()

    if (svgContent.nonEmpty) {
      DownloadHelper.downloadSvg(
        desiredFilename = s"be-program-${System.currentTimeMillis()}.svg",
        svgContent = svgContent
      )
    }
  }

  private lazy val controlPanelContent: Element = {
    val displayPlaceholdersEditor = booleanDisplayConfigEditor(
      "Show placeholders",
      _.displayPlaceholders
    )((cfg, value) => cfg.copy(displayPlaceholders = value))

    val displayNavigationEditor = booleanDisplayConfigEditor(
      "Show navigation",
      _.displayNavigation
    )((cfg, value) => cfg.copy(displayNavigation = value))

    val compactDefinitionsEditor = booleanDisplayConfigEditor(
      "Compact definitions",
      _.compactDefinitions
    )((cfg, value) => cfg.copy(compactDefinitions = value))

    val compactFunctionCallsEditor = booleanDisplayConfigEditor(
      "Compact function calls",
      _.compactFunctionCalls
    )((cfg, value) => cfg.copy(compactFunctionCalls = value))

    div(
      cls := "be-fullscreen-control-content",
      div(
        cls := "be-fullscreen-control-config",
        displayPlaceholdersEditor,
        displayNavigationEditor,
        compactDefinitionsEditor,
        compactFunctionCallsEditor,
        controlFlowSelector
      ),
      button(
        cls := "be-fullscreen-control-download-button",
        typ := "button",
        "download with Control flow",
        onClick --> (_ => downloadCurrentTreeAsSvg())
      )
    )
  }

  override def getDomElement(): Element =
    div(
      cls := "be-fullscreen-panel control",
      h2(
        cls := "be-fullscreen-panel-label",
        "Control"
      ),
      div(
        cls := "be-fullscreen-panel-content",
        controlPanelContent
      )
    )
}
