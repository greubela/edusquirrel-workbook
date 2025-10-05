package interactionPlugins.blockEnvironment.firstIteration

case class TurtleStructuredBlock(
  block: TurtleBlock,
  connectionChildren: Map[String, List[TurtleStructuredBlock]] = Map.empty
) {

  def withChildren(connectionId: String, children: List[TurtleStructuredBlock]): TurtleStructuredBlock =
    copy(connectionChildren = connectionChildren.updated(connectionId, children))

  def childrenFor(connectionId: String): List[TurtleStructuredBlock] =
    connectionChildren.getOrElse(connectionId, Nil)
}

object TurtleStructuredBlock {

  def simple(block: TurtleBlock): TurtleStructuredBlock = TurtleStructuredBlock(block, Map.empty)

  def fromFlatList(blocks: List[TurtleBlock]): List[TurtleStructuredBlock] =
    blocks.map(block => TurtleStructuredBlock(block, Map.empty))

  def flattenCommands(blocks: List[TurtleStructuredBlock]): List[TurtleCommand] = {
    blocks.flatMap { node =>
      node.block.definition.behaviour match {
        case TurtleBlockBehaviour.Command(build) =>
          val expressionMap = parameterExpressionMap(node)
          val context = TurtleBlockContext(node.block, expressionMap)
          val baseCommand = build(context)
          val nestedCommands = flattenCommands(enclosedChildren(node))
          val closingCommands = node.block.definition.closingCommand.toList
          baseCommand :: (nestedCommands ++ closingCommands)
        case _ => Nil
      }
    }
  }

  private def enclosedChildren(node: TurtleStructuredBlock): List[TurtleStructuredBlock] = {
    node.block.definition.connections
      .find(_.kind == TurtleConnectionKind.Enclosed)
      .map(connection => node.childrenFor(connection.id))
      .getOrElse(Nil)
  }

  private def parameterExpressionMap(node: TurtleStructuredBlock): Map[String, TurtleExpression] = {
    node.block.definition.connections.collect {
      case connection if connection.kind == TurtleConnectionKind.Parameter =>
        val expression = buildExpressionForConnection(node, connection)
        connection.id -> expression
    }.toMap
  }

  private def buildExpressionForConnection(
    node: TurtleStructuredBlock,
    connection: TurtleBlockConnection
  ): TurtleExpression = {
    val children = node.childrenFor(connection.id).take(connection.maxChildren)
    if (children.nonEmpty) {
      expressionFromNode(children.head)
    } else {
      connection.defaultChildren().headOption.map(expressionFromNode).getOrElse(TurtleExpression.Literal(0.0))
    }
  }

  private def expressionFromNode(node: TurtleStructuredBlock): TurtleExpression = {
    node.block.definition.behaviour match {
      case TurtleBlockBehaviour.Reporter(_, build) =>
        val childExpressions = parameterExpressionMap(node)
        build(TurtleBlockContext(node.block, childExpressions))
      case _ => TurtleExpression.Literal(node.block.numericValue())
    }
  }
}
