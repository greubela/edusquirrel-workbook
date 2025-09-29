package interactionPlugins.blockProgramming

import workbook.model.states.InteractionState

case class TurtleProgramState(commands: List[TurtleCommand]) extends InteractionState {
  override def getStateAsString(): String = commands.map(_.programText).mkString("\n")
}

object TurtleProgramState {
  val empty: TurtleProgramState = TurtleProgramState(Nil)

  def fromBlocks(blocks: List[TurtleStructuredBlock]): TurtleProgramState = {
    val commands = TurtleStructuredBlock
      .flattenCommands(blocks)
      .filterNot(_ == TurtleCommand.WhenProgramStarted)
    TurtleProgramState(commands)
  }
}

case class TurtleEditorState(program: TurtleProgramState) extends InteractionState {
  override def getStateAsString(): String = program.getStateAsString()
}

case class TurtleScaffoldingState(currentProgram: TurtleProgramState, sampleProgram: TurtleProgramState) extends InteractionState {
  override def getStateAsString(): String = currentProgram.getStateAsString()
}

case class TurtleGradingState(currentProgram: TurtleProgramState, expectedLines: List[TurtleLineSegment]) extends InteractionState {
  override def getStateAsString(): String = currentProgram.getStateAsString()
}
