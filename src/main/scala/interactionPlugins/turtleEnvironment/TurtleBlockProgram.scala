package interactionPlugins.turtleEnvironment

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.Signal

sealed trait TurtlePathSegment

object TurtlePathSegment {
  case class IntoBlock(blockId: String) extends TurtlePathSegment
  case class IntoSocket(blockId: String, socketId: String) extends TurtlePathSegment
}

class TurtleBlockProgram(
  initialBlocks: List[TurtleStructuredBlock],
  onProgramStateChanged: TurtleProgramState => Unit
) {

  import TurtlePathSegment.*

  type BlockPath = List[TurtlePathSegment]

  private val blocksVar: Var[List[TurtleStructuredBlock]] =
    Var(ensureRoot(initialBlocks))

  val rootPath: BlockPath = Nil

  def blocksSignal: Signal[List[TurtleStructuredBlock]] = blocksVar.signal

  def currentBlocks: List[TurtleStructuredBlock] = blocksVar.now()

  def toProgramState: TurtleProgramState = TurtleProgramState.fromBlocks(currentBlocks)

  def setBlocks(newBlocks: List[TurtleStructuredBlock]): Unit =
    updateState(newBlocks)

  def insertBlocks(path: BlockPath, index: Int, newBlocks: List[TurtleStructuredBlock]): Unit = {
    val sanitized = sanitizeBlocksForPath(path, newBlocks)
    if (sanitized.nonEmpty) {
      val updated = path.lastOption match {
        case Some(_: IntoSocket) =>
          transformStack(currentBlocks, path)(_ => sanitized)
        case _ =>
          transformStack(currentBlocks, path) { stack =>
            val safeIndex = clampInsertionIndex(path, index, stack.length)
            val (prefix, suffix) = stack.splitAt(safeIndex)
            prefix ++ sanitized ++ suffix
          }
      }
      updateState(updated)
    }
  }

  def updateBlockValue(blockId: String, newValue: Double): Unit = {
    val (updated, changed) = updateBlockRecursive(currentBlocks, blockId, _.updateValue(newValue))
    if (changed) updateState(updated)
  }

  def removeBlock(blockId: String): Unit = {
    val target = findBlockById(currentBlocks, blockId)
    target match {
      case Some(node) if node.block.definition.key != TurtleBlockLibrary.whenProgramStarted.key =>
        val (updated, removed) = removeBlockRecursive(currentBlocks, blockId)
        if (removed) updateState(updated)
      case _ => ()
    }
  }

  def detachFrom(path: BlockPath, index: Int): List[TurtleStructuredBlock] = {
    var detached: List[TurtleStructuredBlock] = Nil
    val updated = transformStack(currentBlocks, path) { stack =>
      if (stack.isEmpty) stack
      else {
        val safeIndex = clampRemovalIndex(path, index, stack.length)
        val (prefix, suffix) = stack.splitAt(safeIndex)
        detached = suffix
        prefix
      }
    }
    updateState(updated)
    detached
  }

  private def sanitizeBlocksForPath(path: BlockPath, blocks: List[TurtleStructuredBlock]): List[TurtleStructuredBlock] = {
    path.lastOption match {
      case Some(_: IntoSocket) =>
        findSocketDefinition(path) match {
          case Some(socket) =>
            blocks.collect {
              case node if node.block.definition.behaviour match {
                    case TurtleBlockBehaviour.Reporter(valueType, _) => valueType == socket.valueType
                    case _                                          => false
                  } => node
            }.take(socket.maxChildren)
          case None => Nil
        }
      case _ =>
        blocks.collect {
          case node if node.block.definition.behaviour.isInstanceOf[TurtleBlockBehaviour.Command] &&
              node.block.definition.key != TurtleBlockLibrary.whenProgramStarted.key => node
        }
    }
  }

  private def transformStack(
    blocks: List[TurtleStructuredBlock],
    path: BlockPath
  )(modify: List[TurtleStructuredBlock] => List[TurtleStructuredBlock]): List[TurtleStructuredBlock] = {
    path match {
      case Nil => modify(blocks)
      case IntoBlock(blockId) :: tail =>
        blocks.map { node =>
          if (node.block.id == blockId)
            node.withInside(transformStack(node.inside, tail)(modify))
          else node
        }
      case IntoSocket(blockId, socketId) :: tail =>
        blocks.map { node =>
          if (node.block.id == blockId) {
            val children = node.socketContent(socketId)
            val updatedChildren =
              if (tail.isEmpty) modify(children)
              else transformStack(children, tail)(modify)
            node.withSocketChildren(socketId, updatedChildren)
          } else node
        }
    }
  }

  private def updateBlockRecursive(
    blocks: List[TurtleStructuredBlock],
    blockId: String,
    update: TurtleBlock => TurtleBlock
  ): (List[TurtleStructuredBlock], Boolean) = {
    blocks match {
      case Nil => (Nil, false)
      case head :: tail =>
        if (head.block.id == blockId) {
          (head.copy(block = update(head.block)) :: tail, true)
        } else {
          val (updatedInside, insideChanged) = updateBlockRecursive(head.inside, blockId, update)
          if (insideChanged) {
            (head.withInside(updatedInside) :: tail, true)
          } else {
            val (updatedSockets, socketChanged) = updateSockets(head.socketChildren, blockId, update)
            if (socketChanged) {
              (head.copy(socketChildren = updatedSockets) :: tail, true)
            } else {
              val (updatedTail, changedTail) = updateBlockRecursive(tail, blockId, update)
              (head :: updatedTail, changedTail)
            }
          }
        }
    }
  }

  private def updateSockets(
    sockets: Map[String, List[TurtleStructuredBlock]],
    blockId: String,
    update: TurtleBlock => TurtleBlock
  ): (Map[String, List[TurtleStructuredBlock]], Boolean) = {
    var changed = false
    val updated = sockets.map { case (key, nodes) =>
      val (updatedNodes, nodeChanged) = updateBlockRecursive(nodes, blockId, update)
      if (nodeChanged) changed = true
      key -> updatedNodes
    }
    (updated, changed)
  }

  private def removeBlockRecursive(
    blocks: List[TurtleStructuredBlock],
    blockId: String
  ): (List[TurtleStructuredBlock], Boolean) = {
    blocks match {
      case Nil => (Nil, false)
      case head :: tail =>
        if (head.block.id == blockId) {
          (tail, true)
        } else {
          val (updatedInside, insideRemoved) = removeBlockRecursive(head.inside, blockId)
          if (insideRemoved) {
            (head.withInside(updatedInside) :: tail, true)
          } else {
            val (updatedSockets, socketRemoved) = removeFromSockets(head.socketChildren, blockId)
            if (socketRemoved) {
              (head.copy(socketChildren = updatedSockets) :: tail, true)
            } else {
              val (updatedTail, removedInTail) = removeBlockRecursive(tail, blockId)
              (head :: updatedTail, removedInTail)
            }
          }
        }
    }
  }

  private def removeFromSockets(
    sockets: Map[String, List[TurtleStructuredBlock]],
    blockId: String
  ): (Map[String, List[TurtleStructuredBlock]], Boolean) = {
    var changed = false
    val updated = sockets.map { case (key, nodes) =>
      val (updatedNodes, removed) = removeBlockRecursive(nodes, blockId)
      if (removed) changed = true
      key -> updatedNodes
    }
    (updated, changed)
  }

  private def findBlockById(
    blocks: List[TurtleStructuredBlock],
    blockId: String
  ): Option[TurtleStructuredBlock] = {
    blocks match {
      case Nil => None
      case head :: tail =>
        if (head.block.id == blockId) Some(head)
        else {
          findBlockById(head.inside, blockId) match {
            case some @ Some(_) => some
            case None =>
              head.socketChildren.values.iterator
                .map(children => findBlockById(children, blockId))
                .collectFirst { case Some(node) => node }
                .orElse(findBlockById(tail, blockId))
          }
        }
    }
  }

  private def clampInsertionIndex(path: BlockPath, index: Int, size: Int): Int = {
    val base = math.max(0, math.min(index, size))
    if (path.isEmpty) math.max(1, base) else base
  }

  private def clampRemovalIndex(path: BlockPath, index: Int, size: Int): Int = {
    val maxIndex = math.max(0, size - 1)
    val base = math.max(0, math.min(index, maxIndex))
    if (path.isEmpty) math.max(1, base) else base
  }

  private def ensureRoot(blocks: List[TurtleStructuredBlock]): List[TurtleStructuredBlock] = {
    val (maybeRoot, rest) = blocks.foldLeft((Option.empty[TurtleStructuredBlock], List.empty[TurtleStructuredBlock])) {
      case ((None, acc), node) if node.block.definition.key == TurtleBlockLibrary.whenProgramStarted.key => (Some(node), acc)
      case ((rootOpt, acc), node) if node.block.definition.key == TurtleBlockLibrary.whenProgramStarted.key => (rootOpt, acc)
      case ((rootOpt, acc), node) => (rootOpt, acc :+ node)
    }
    val rootBlock = maybeRoot.getOrElse(TurtleBlockLibrary.instantiateWithCompanion(TurtleBlockLibrary.whenProgramStarted).head)
    rootBlock :: rest
  }

  private def updateState(newBlocks: List[TurtleStructuredBlock]): Unit = {
    val ensured = ensureRoot(newBlocks)
    blocksVar.set(ensured)
    notifyStateChanged(ensured)
  }

  private def notifyStateChanged(blocks: List[TurtleStructuredBlock]): Unit =
    onProgramStateChanged(TurtleProgramState.fromBlocks(blocks))

  private def findSocketDefinition(path: BlockPath): Option[TurtleBlockSocketDefinition] = {
    def loop(blocks: List[TurtleStructuredBlock], remaining: BlockPath): Option[TurtleBlockSocketDefinition] = {
      remaining match {
        case Nil => None
        case IntoBlock(blockId) :: tail =>
          blocks.find(_.block.id == blockId).flatMap(node => loop(node.inside, tail))
        case IntoSocket(blockId, socketId) :: tail =>
          blocks.find(_.block.id == blockId).flatMap { node =>
            if (tail.isEmpty) node.block.definition.sockets.find(_.id == socketId)
            else loop(node.socketContent(socketId), tail)
          }
      }
    }
    loop(currentBlocks, path)
  }
}
