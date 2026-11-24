package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.util.logging.Logger

/** What the surrounding page supplies to Eva and, through [[EvaEditorContext]], to every language editor. */
final case class EvaEditorEnvironment(logger: Logger, labels: EvaEditorLabels, humanLanguage: Signal[HumanLanguage])
