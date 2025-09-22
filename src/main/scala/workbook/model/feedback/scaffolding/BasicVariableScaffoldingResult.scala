package workbook.model.feedback.scaffolding

import workbook.model.feedback.FeedbackStatus

case class BasicVariableScaffoldingResult[T](variable: T, status: FeedbackStatus) extends ScaffoldingResult{

}


type BasicStringScaffoldingResult = BasicVariableScaffoldingResult[String]
