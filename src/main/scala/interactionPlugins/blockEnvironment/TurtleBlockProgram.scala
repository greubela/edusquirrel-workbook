package interactionPlugins.blockProgramming

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.Signal

sealed trait TurtlePathSegment

object TurtlePathSegment {
  case class IntoConnection(blockId: String, connectionId: String) extends TurtlePathSegment
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
      val connectionKind = connectionForPath(path).map(_.kind)
      val updated = connectionKind match {
        case Some(TurtleConnectionKind.Parameter) =>
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

  def previewDetach(path: BlockPath, index: Int): List[TurtleStructuredBlock] = {
    var detached: List[TurtleStructuredBlock] = Nil
    val connectionKind = connectionForPath(path).map(_.kind)
    transformStack(currentBlocks, path) { stack =>
      if (stack.isEmpty) stack
      else connectionKind match {
        case Some(TurtleConnectionKind.Parameter) =>
          detached = stack
          stack
        case _ =>
          val safeIndex = clampRemovalIndex(path, index, stack.length)
          val (_, suffix) = stack.splitAt(safeIndex)
          detached = suffix
          stack
      }
    }
    detached
  }

  def moveBlocks(sourcePath: BlockPath, sourceIndex: Int, targetPath: BlockPath, targetIndex: Int): Unit = {
    val candidate = previewDetach(sourcePath, sourceIndex)
    if (candidate.nonEmpty) {
      val sanitizedForTarget = sanitizeBlocksForPath(targetPath, candidate)
      if (sanitizedForTarget.nonEmpty) {
        val detached = detachFrom(sourcePath, sourceIndex)
        insertBlocks(targetPath, targetIndex, detached)
      }
    }
  }

  def detachFrom(path: BlockPath, index: Int): List[TurtleStructuredBlock] = {
    var detached: List[TurtleStructuredBlock] = Nil
    val connectionKind = connectionForPath(path).map(_.kind)
    val updated = transformStack(currentBlocks, path) { stack =>
      if (stack.isEmpty) stack
      else connectionKind match {
        case Some(TurtleConnectionKind.Parameter) =>
          detached = stack
          Nil
        case _ =>
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
    val connectionOpt = connectionForPath(path)
    connectionOpt match {
      case Some(connection) =>
        val accepted = blocks.collect {
          case node if connection.acceptTypes.contains(node.block.definition.evaluatesTo) &&
              node.block.definition.key != TurtleBlockLibrary.whenProgramStarted.key => node
        }
        connection.kind match {
          case TurtleConnectionKind.Parameter => accepted.take(connection.maxChildren)
          case _ => accepted
        }
      case None =>
        blocks.collect {
          case node if node.block.definition.evaluatesTo == TurtleDataType.Unit &&
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
      case IntoConnection(blockId, connectionId) :: tail =>
        blocks.map { node =>
          if (node.block.id == blockId) {
            val children = node.childrenFor(connectionId)
            val updatedChildren =
              if (tail.isEmpty) modify(children)
              else transformStack(children, tail)(modify)
            node.withChildren(connectionId, updatedChildren)
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
          val (updatedConnections, connectionChanged) = updateConnections(head.connectionChildren, blockId, update)
          if (connectionChanged) {
            (head.copy(connectionChildren = updatedConnections) :: tail, true)
          } else {
            val (updatedTail, changedTail) = updateBlockRecursive(tail, blockId, update)
            (head :: updatedTail, changedTail)
          }
        }
    }
  }

  private def updateConnections(
    connections: Map[String, List[TurtleStructuredBlock]],
    blockId: String,
    update: TurtleBlock => TurtleBlock
  ): (Map[String, List[TurtleStructuredBlock]], Boolean) = {
    var changed = false
    val updated = connections.map { case (key, nodes) =>
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
          val (updatedConnections, removedFromConnections) = removeFromConnections(head.connectionChildren, blockId)
          if (removedFromConnections) {
            (head.copy(connectionChildren = updatedConnections) :: tail, true)
          } else {
            val (updatedTail, removedInTail) = removeBlockRecursive(tail, blockId)
            (head :: updatedTail, removedInTail)
          }
        }
    }
  }

  private def removeFromConnections(
    connections: Map[String, List[TurtleStructuredBlock]],
    blockId: String
  ): (Map[String, List[TurtleStructuredBlock]], Boolean) = {
    var changed = false
    val updated = connections.map { case (key, nodes) =>
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
          val connectionSearch = head.connectionChildren.values.iterator
            .map(children => findBlockById(children, blockId))
            .collectFirst { case Some(node) => node }
          connectionSearch.orElse(findBlockById(tail, blockId))
        }
    }
  }

  private def clampInsertionIndex(path: BlockPath, index: Int, size: Int): Int = {
    val connectionKind = connectionForPath(path).map(_.kind)
    val base = math.max(0, math.min(index, size))
    if (path.isEmpty) math.max(1, base)
    else connectionKind match {
      case Some(TurtleConnectionKind.Parameter) => 0
      case _ => base
    }
  }

  private def clampRemovalIndex(path: BlockPath, index: Int, size: Int): Int = {
    val connectionKind = connectionForPath(path).map(_.kind)
    val maxIndex = math.max(0, size - 1)
    val base = math.max(0, math.min(index, maxIndex))
    if (path.isEmpty) math.max(1, base)
    else connectionKind match {
      case Some(TurtleConnectionKind.Parameter) => 0
      case _ => base
    }
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

  private def connectionForPath(path: BlockPath): Option[TurtleBlockConnection] = {
    path.lastOption match {
      case Some(IntoConnection(blockId, connectionId)) =>
        findBlockById(currentBlocks, blockId).flatMap(_.block.definition.connection(connectionId))
      case None => None
    }
  }
}
