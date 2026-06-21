package it.evadid.evacuation.shared.traits.graphic

trait EvaMouseListener {

  def onMouseEntered(x: Double, y: Double): Unit

  def onMouseExited(x: Double, y: Double): Unit

  def onMouseClicked(x: Double, y: Double, primaryButton: Boolean): Unit

  def onMouseMoved(x: Double, y: Double): Unit

}

object EvaMouseListener {

  def funtionOnActionListener(functionOnAction: () => Unit): EvaMouseListener = new EvaMouseListener {
    override def onMouseEntered(x: Double, y: Double): Unit = functionOnAction.apply()

    override def onMouseExited(x: Double, y: Double): Unit = functionOnAction.apply()

    override def onMouseClicked(x: Double, y: Double, primaryButton: Boolean): Unit = functionOnAction.apply()

    override def onMouseMoved(x: Double, y: Double): Unit = functionOnAction.apply()
  }

}
