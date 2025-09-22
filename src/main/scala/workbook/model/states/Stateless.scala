package workbook.model.states


case class Stateless() extends InteractionState {
  override def getStateAsString(): String = ""
}
object Stateless {
  val StatelessInstance: Stateless = Stateless()
}
