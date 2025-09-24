package interactionPlugins.turtleEnvironment

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.Signal

class TurtleBlockProgram(
  initialBlocks: List[TurtleBlock],
  onProgramStateChanged: TurtleProgramState => Unit
) {

  private val blocksVar: Var[List[TurtleBlock]] = Var(ensureRoot(initialBlocks))

  def blocksSignal: Signal[List[TurtleBlock]] = blocksVar.signal

  def currentBlocks: List[TurtleBlock] = blocksVar.now()

  def toProgramState: TurtleProgramState = TurtleProgramState.fromBlocks(currentBlocks)

  def setBlocks(newBlocks: List[TurtleBlock]): Unit = {
    blocksVar.set(ensureRoot(newBlocks))
    notifyStateChanged()
  }

  def insertBlocks(atIndex: Int, newBlocks: List[TurtleBlock]): Unit = {
    val clamped = clampInsertionIndex(atIndex)
    val current = currentBlocks
    val (prefix, suffix) = current.splitAt(clamped)
    blocksVar.set(prefix ++ newBlocks ++ suffix)
    notifyStateChanged()
  }

  def updateBlockValue(blockId: String, newValue: Double): Unit = {
    val updated = currentBlocks.map { block =>
      if (block.id == blockId) block.updateValue(newValue) else block
    }
    blocksVar.set(updated)
    notifyStateChanged()
  }

  def removeBlock(blockId: String): Unit = {
    val updated = currentBlocks.filterNot(_.id == blockId)
    blocksVar.set(ensureRoot(updated))
    notifyStateChanged()
  }

  def detachFrom(index: Int): List[TurtleBlock] = {
    val safeIndex = clampInsertionIndex(index)
    val current = currentBlocks
    val (prefix, suffix) = current.splitAt(safeIndex)
    blocksVar.set(prefix)
    notifyStateChanged()
    suffix
  }

  private def clampInsertionIndex(index: Int): Int = {
    val size = currentBlocks.length
    val safe = math.max(1, math.min(index, size))
    safe
  }

  private def ensureRoot(blocks: List[TurtleBlock]): List[TurtleBlock] = {
    val headIsRoot = blocks.headOption.exists(_.command == TurtleCommand.WhenProgramStarted)
    if (headIsRoot) blocks
    else TurtleBlockLibrary.whenProgramStarted.createInstance() :: blocks.filterNot(_.command == TurtleCommand.WhenProgramStarted)
  }

  private def notifyStateChanged(): Unit = onProgramStateChanged(TurtleProgramState.fromBlocks(currentBlocks))
}
