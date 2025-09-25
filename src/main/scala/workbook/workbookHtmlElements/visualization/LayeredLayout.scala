package workbook.workbookHtmlElements.visualization

import scala.collection.mutable
import workbook.model.exercise.ExerciseSection

object LayeredLayout {

  def compute(sections: List[ExerciseSection], config: VisualizationConfig): WorkbookLayout = {
    val nodes = sections.zipWithIndex.map { case (section, idx) => new SectionNode(section, idx) }
    if (nodes.isEmpty) {
      return WorkbookLayout(Nil, Nil, config.marginX * 2, config.marginY * 2)
    }

    val edges = buildEdges(sections, nodes)
    computeBubbleGeometry(nodes, config)
    assignLayers(nodes, edges)
    orderWithinLayers(nodes, edges)
    assignCoordinates(nodes, config)
    val (width, height) = computeCanvasSize(nodes, config)
    WorkbookLayout(nodes, edges, width, height)
  }

  private def buildEdges(sections: List[ExerciseSection], nodes: List[SectionNode]): List[Edge] = {
    val indexBySection = sections.zipWithIndex.toMap
    val uniqueEdges = mutable.LinkedHashSet[(Int, Int, DependencyType)]()

    sections.zipWithIndex.foreach { case (section, index) =>
      section.sectionsRequiredBefore.foreach { before =>
        indexBySection.get(before).foreach { sourceIndex =>
          uniqueEdges.add((sourceIndex, index, DependencyType.Required))
        }
      }
      section.sectionsRecommendedBefore.foreach { before =>
        indexBySection.get(before).foreach { sourceIndex =>
          uniqueEdges.add((sourceIndex, index, DependencyType.Recommended))
        }
      }
    }

    uniqueEdges.toList.map { case (source, target, dependencyType) => Edge(source, target, dependencyType) }
  }

  private def computeBubbleGeometry(nodes: List[SectionNode], config: VisualizationConfig): Unit = {
    val allDurations = nodes.flatMap(_.section.exercies.map(_.estimatedTimeInMinutes)).filter(_ > 0)
    val maxDuration = if (allDurations.isEmpty) 1.0 else allDurations.max

    nodes.foreach { node =>
      val exercises = node.section.exercies
      val bubbleLayouts = exercises.zipWithIndex.map { case (exercise, idx) =>
        val relativeWidth = if (maxDuration <= 0) config.bubbleMinWidth
        else {
          val normalized = exercise.estimatedTimeInMinutes / maxDuration
          val scaled = config.bubbleMinWidth + normalized * (config.bubbleMaxWidth - config.bubbleMinWidth)
          math.max(config.bubbleMinWidth, math.min(config.bubbleMaxWidth, scaled))
        }
        val relativeX = config.sectionPaddingX
        val relativeY = config.sectionPaddingY + config.titleHeight + config.titleSpacing + idx * (config.bubbleHeight + config.bubbleSpacing)
        ExerciseBubbleLayout(exercise, relativeWidth, config.bubbleHeight, relativeX, relativeY)
      }

      val contentWidth = bubbleLayouts.map(_.width).maxOption.getOrElse(config.bubbleMinWidth)
      val bubbleAreaHeight = if (bubbleLayouts.isEmpty) config.emptySectionPlaceholderHeight
      else bubbleLayouts.size * config.bubbleHeight + (bubbleLayouts.size - 1) * config.bubbleSpacing

      val width = math.max(config.sectionMinWidth, contentWidth + config.sectionPaddingX * 2)
      val height = config.sectionPaddingY * 2 + config.titleHeight + config.titleSpacing + bubbleAreaHeight

      node.width = width
      node.height = height
      node.bubbleLayouts = bubbleLayouts
      node.bubbleAreaTop = config.sectionPaddingY + config.titleHeight + config.titleSpacing
      node.bubbleAreaHeight = bubbleAreaHeight
    }
  }

  private def assignLayers(nodes: List[SectionNode], edges: List[Edge]): Unit = {
    val requiredEdges = edges.filter(_.dependencyType.isRequired)
    val outgoingRequired = requiredEdges.groupBy(_.source).withDefaultValue(Nil)
    val incomingRequired = requiredEdges.groupBy(_.target).withDefaultValue(Nil)

    val indegree = Array.fill(nodes.size)(0)
    val maxLayerFromPred = Array.fill(nodes.size)(0)
    incomingRequired.foreach { case (target, incoming) => indegree(target) = incoming.size }

    val queue = mutable.PriorityQueue[Int]()(Ordering.by[Int, Int](i => -nodes(i).index))
    nodes.indices.foreach { idx => if (indegree(idx) == 0) queue.enqueue(idx) }

    while (queue.nonEmpty) {
      val current = queue.dequeue()
      val node = nodes(current)
      node.layer = math.max(node.layer, maxLayerFromPred(current))
      outgoingRequired(current).foreach { edge =>
        val target = edge.target
        maxLayerFromPred(target) = math.max(maxLayerFromPred(target), node.layer + 1)
        indegree(target) -= 1
        if (indegree(target) == 0) {
          queue.enqueue(target)
        }
      }
    }

    val allEdges = edges
    var changed = true
    while (changed) {
      changed = false
      allEdges.foreach { edge =>
        val sourceLayer = nodes(edge.source).layer
        val target = nodes(edge.target)
        val desired = sourceLayer + 1
        if (target.layer < desired) {
          target.layer = desired
          changed = true
        }
      }
    }
  }

  private def orderWithinLayers(nodes: List[SectionNode], edges: List[Edge]): Unit = {
    val layers = nodes.groupBy(_.layer).view.mapValues(_.sortBy(_.index)).toMap
    layers.foreach { case (_, layerNodes) =>
      layerNodes.zipWithIndex.foreach { case (node, order) => node.order = order }
    }

    val incomingByTarget = edges.groupBy(_.target).withDefaultValue(Nil)
    val outgoingBySource = edges.groupBy(_.source).withDefaultValue(Nil)

    val sortedLayerIndices = nodes.map(_.layer).distinct.sorted
    val maxIterations = 4

    def barycenterOrder(layerNodes: List[SectionNode], neighbours: SectionNode => List[SectionNode]): List[SectionNode] = {
      val scored = layerNodes.map { node =>
        val neighborNodes = neighbours(node)
        val score = if (neighborNodes.isEmpty) node.order.toDouble
        else neighborNodes.map(_.order.toDouble).sum / neighborNodes.size
        (node, score)
      }
      scored.sortBy { case (node, score) => (score, node.order, node.index) }.map(_._1)
    }

    def neighboursFromIncoming(node: SectionNode): List[SectionNode] =
      incomingByTarget(node.index).map(edge => nodes(edge.source))

    def neighboursFromOutgoing(node: SectionNode): List[SectionNode] =
      outgoingBySource(node.index).map(edge => nodes(edge.target))

    var currentOrder = layers.map { case (layerIndex, layerNodes) => layerIndex -> layerNodes.sortBy(_.order) }

    (0 until maxIterations).foreach { _ =>
      sortedLayerIndices.tail.foreach { layerIndex =>
        val updated = barycenterOrder(currentOrder(layerIndex), neighboursFromIncoming)
        updated.zipWithIndex.foreach { case (node, order) => node.order = order }
        currentOrder = currentOrder.updated(layerIndex, updated)
      }

      sortedLayerIndices.dropRight(1).reverse.foreach { layerIndex =>
        val updated = barycenterOrder(currentOrder(layerIndex), neighboursFromOutgoing)
        updated.zipWithIndex.foreach { case (node, order) => node.order = order }
        currentOrder = currentOrder.updated(layerIndex, updated)
      }
    }
  }

  private def assignCoordinates(nodes: List[SectionNode], config: VisualizationConfig): Unit = {
    val nodesByLayer = nodes.groupBy(_.layer).view.mapValues(_.sortBy(_.order)).toMap
    val sortedLayerIndices = nodesByLayer.keys.toList.sorted

    var currentX = config.marginX
    sortedLayerIndices.foreach { layerIndex =>
      val layerNodes = nodesByLayer(layerIndex)
      val layerWidth = layerNodes.map(_.width).maxOption.getOrElse(0.0)
      var currentY = config.marginY
      layerNodes.foreach { node =>
        node.x = currentX + (layerWidth - node.width) / 2
        node.y = currentY
        currentY += node.height + config.verticalNodeSpacing
      }
      currentX += layerWidth + config.horizontalLayerSpacing
    }
  }

  private def computeCanvasSize(nodes: List[SectionNode], config: VisualizationConfig): (Double, Double) = {
    val maxWidth = nodes.map(node => node.x + node.width).maxOption.getOrElse(0.0)
    val maxHeight = nodes.map(node => node.y + node.height).maxOption.getOrElse(0.0)
    val width = maxWidth + config.marginX
    val height = maxHeight + config.marginY
    (width, height)
  }
}
