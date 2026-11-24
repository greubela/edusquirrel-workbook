package it.evadid.homepage.webElements.editor.code.EvaEditor

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

private[code] final case class PendingEditorConversion(source: ProgrammingState, result: scala.concurrent.Future[it.evadid.vm.conversion.ConversionResult]) extends EvaConversionCheck
