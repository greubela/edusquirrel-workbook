package interactionPlugins.blockEnvironment.config

sealed trait ControlFlowDisplay

object ControlFlowDisplay {
  case object ControlFlowHidden extends ControlFlowDisplay
  case object ControlFlowBackgrounds extends ControlFlowDisplay
  case object ControlFlowShownFull extends ControlFlowDisplay
}

case class BeTreeDisplayConfig(
    displayPlaceholders: Boolean,
    displayNavigation: Boolean,
    controlFlowDisplay: ControlFlowDisplay,
    compactDefinitions: Boolean,
    compactFunctionCalls: Boolean
)

object BeTreeDisplayConfig {
  val editorDefaults: BeTreeDisplayConfig = BeTreeDisplayConfig(
    displayPlaceholders = true,
    displayNavigation = true,
    controlFlowDisplay = ControlFlowDisplay.ControlFlowShownFull,
    compactDefinitions = true,
    compactFunctionCalls = true
  )

  val libraryDefaults: BeTreeDisplayConfig = BeTreeDisplayConfig(
    displayPlaceholders = false,
    displayNavigation = false,
    controlFlowDisplay = ControlFlowDisplay.ControlFlowHidden,
    compactDefinitions = true,
    compactFunctionCalls = true
  )

  val previewDefaults: BeTreeDisplayConfig = BeTreeDisplayConfig(
    displayPlaceholders = false,
    displayNavigation = false,
    controlFlowDisplay = ControlFlowDisplay.ControlFlowShownFull,
    compactDefinitions = true,
    compactFunctionCalls = true
  )
}
