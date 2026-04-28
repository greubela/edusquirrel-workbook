package workbook.model.interaction

import workbook.model.interaction.InteractionVariableState.SerializedExerciseVariableState
import workbook.model.interaction.history.UpdateImportance

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*

case class SerializedInteractionHistory(keyForSerialization: String, states: List[SerializedExerciseVariableState]) {
  override lazy val toString: String = upickle.default.write(this)
}

object SerializedInteractionHistory {

  private given upickle.ReadWriter[UpdateImportance] = upickle.readwriter[String].bimap[UpdateImportance](_.toString, UpdateImportance.valueOf)

  private given upickle.ReadWriter[SerializedExerciseVariableState] = upickle.macroRW

  private given upickle.ReadWriter[List[SerializedExerciseVariableState]] =
    upickle.readwriter[Seq[SerializedExerciseVariableState]].bimap[List[SerializedExerciseVariableState]](identity, _.toList)

  private given upickle.ReadWriter[SerializedInteractionHistory] = upickle.macroRW


  def apply(str: String): SerializedInteractionHistory = upickle.default.read(str)
}
