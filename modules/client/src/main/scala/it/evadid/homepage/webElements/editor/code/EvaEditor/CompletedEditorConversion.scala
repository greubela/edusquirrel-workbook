package it.evadid.homepage.webElements.editor.code.EvaEditor

import it.evadid.workbook.interactions.programming.state.ProgrammingState

private[code] final case class CompletedEditorConversion(source: ProgrammingState, result: it.evadid.vm.conversion.ConversionResult) extends EvaConversionCheck
