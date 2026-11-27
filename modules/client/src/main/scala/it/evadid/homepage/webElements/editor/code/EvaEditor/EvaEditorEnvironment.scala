package it.evadid.homepage.webElements.editor.code.EvaEditor

import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.util.logging.Logger

/** What the surrounding page supplies to Eva and, through [[EvaEditorContext]], to every language editor: logging,
  * labels, the display language and the libraries the exercise provides (`programming`), in which conversions,
  * analyses and runs read the answer. Project downloads are named after `projectName` (the exercise id) and the date.
  */
final case class EvaEditorEnvironment(logger: Logger, labels: EvaEditorLabels, humanLanguage: Signal[HumanLanguage],
    programming: ProgrammingEnvironment, projectName: String = "program")
