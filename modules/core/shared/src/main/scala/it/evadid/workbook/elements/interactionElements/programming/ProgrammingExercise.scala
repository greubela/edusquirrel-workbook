package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.core.util.io.Serializer
import it.evadid.vm.BeProgram
import it.evadid.vm.test.BeTestSuite
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}

case class ProgrammingExercise(override val id: String, testSuite: Option[BeTestSuite] = None) extends WorkbookInteractionElement[BeProgram] {

  override val defaultValue: BeProgram = BeProgram.miniProgram()
  override val serializer: Serializer[BeProgram] = new Serializer[BeProgram]() {
    override def serialize(obj: BeProgram): String = obj.fullProgram.expressionIO.toStringInLanguage(Python, English, false)

    override def deserialize(str: String): BeProgram = {
      println("ProgrammingExercise::deserialize does not work yet!")
      BeProgram.empty
    }
  }
  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
}
