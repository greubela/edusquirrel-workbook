package it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle

import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand

enum TurtleExecutionPanelStatus {
  case Idle, Running, Stopped
  case Ready(commands: List[TurtleCommand[Double]], matches: Option[Boolean])
  case Failed(message: String)
}
