package interactionPlugins.programmingExercise.pythonExercise.pyodide

import scala.scalajs.js

final case class ModuleBinding(
    moduleName: String,
    callbacks: Map[String, Seq[js.Any] => Unit]
)
