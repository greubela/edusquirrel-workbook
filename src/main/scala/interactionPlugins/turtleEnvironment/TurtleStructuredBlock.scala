package interactionPlugins.turtleEnvironment

case class TurtleStructuredBlock(
  block: TurtleBlock,
  inside: List[TurtleStructuredBlock] = Nil,
  socketChildren: Map[String, List[TurtleStructuredBlock]] = Map.empty
) {
  def withInside(newInside: List[TurtleStructuredBlock]): TurtleStructuredBlock = copy(inside = newInside)

  def withSocketChildren(socketId: String, children: List[TurtleStructuredBlock]): TurtleStructuredBlock =
    copy(socketChildren = socketChildren.updated(socketId, children))

  def socketContent(socketId: String): List[TurtleStructuredBlock] = socketChildren.getOrElse(socketId, Nil)
}

object TurtleStructuredBlock {

  def simple(block: TurtleBlock): TurtleStructuredBlock = TurtleStructuredBlock(block, Nil, Map.empty)

  def fromFlatList(blocks: List[TurtleBlock]): List[TurtleStructuredBlock] =
    blocks.map(block => TurtleStructuredBlock(block, Nil, Map.empty))

  def flattenCommands(blocks: List[TurtleStructuredBlock]): List[TurtleCommand] = {
    blocks.flatMap { node =>
      node.block.definition.behaviour match {
        case TurtleBlockBehaviour.Command(build) =>
          val socketExpressions = socketExpressionMap(node)
          val context = TurtleBlockContext(node.block, socketExpressions)
          val base = build(context)
          val nested = flattenCommands(node.inside)
          val closing = node.block.definition.closingCommand.toList
          base :: (nested ++ closing)
        case _ => Nil
      }
    }
  }

  private def socketExpressionMap(node: TurtleStructuredBlock): Map[String, TurtleExpression] = {
    node.block.definition.sockets.map { socket =>
      val expression = buildExpressionForSocket(node, socket)
      socket.id -> expression
    }.toMap
  }

  private def buildExpressionForSocket(
    node: TurtleStructuredBlock,
    socket: TurtleBlockSocketDefinition
  ): TurtleExpression = {
    val children = node.socketChildren.getOrElse(socket.id, Nil).take(socket.maxChildren)
    if (children.nonEmpty) {
      expressionFromNode(children.head)
    } else {
      TurtleExpression.defaultForSocket(socket)
    }
  }

  private def expressionFromNode(node: TurtleStructuredBlock): TurtleExpression = {
    node.block.definition.behaviour match {
      case TurtleBlockBehaviour.Reporter(_, build) =>
        val socketExpressions = socketExpressionMap(node)
        build(TurtleBlockContext(node.block, socketExpressions))
      case _ => TurtleExpression.Literal(node.block.numericValue())
    }
  }
}
