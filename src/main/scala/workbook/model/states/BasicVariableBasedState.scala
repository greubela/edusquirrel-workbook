package workbook.model.states

case class BasicVariableBasedState[T](variable: T, toStr: T => String) extends InteractionState {
  override def getStateAsString(): String = toStr(variable)
}

object BasicVariableBasedState {

  def createStringState(text: String) = new BasicStringState(text, str => str)

  def createIntState(number: Int) = new BasicIntState(number, num => num.toString)

  def createFloatingState(number: Double) = new BasicFloatingState(number, num => num.toString)


  type BasicStringState = BasicVariableBasedState[String]
  type BasicIntState = BasicVariableBasedState[Int]
  type BasicFloatingState = BasicVariableBasedState[Double]
  type BasicListState[T] = BasicVariableBasedState[List[T]]

}