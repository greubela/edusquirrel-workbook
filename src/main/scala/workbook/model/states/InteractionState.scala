package workbook.model.states

import java.time.LocalTime

trait InteractionState {
  def getStateCreatedAt(): LocalTime = LocalTime.now()

  def getStateAsString(): String
}