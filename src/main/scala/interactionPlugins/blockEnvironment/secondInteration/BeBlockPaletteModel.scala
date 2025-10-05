package interactionPlugins.blockEnvironment.secondInteration

import contentmanagement.datastructures.tree.Tree
import contentmanagement.datastructures.tree.nodeImpl.{NodeBasedTreeImpl, NodeBasedTreePosition}
import interactionPlugins.blockEnvironment.programming.{BeBlock, BeProgram}
import interactionPlugins.blockEnvironment.programming.blocks.BeBlockFunctionDefinition
import interactionPlugins.blockEnvironment.programming.connection.{BeConnectionRole, FunctionBody}

/**
 * Holds metadata about the block palette. Each palette category exposes a set of entries
 * that can instantiate new [[BeBlock]] instances on demand.
 */
final case class BeBlockPaletteModel(categories: List[BePaletteCategory]) {

  private val entryIndex: Map[String, BePaletteEntry] =
    categories.flatMap(category => category.entries.map(entry => entry.id -> entry)).toMap

  def entriesFor(category: BePaletteCategory): List[BePaletteEntry] = category.entries

  def entryById(id: String): Option[BePaletteEntry] = entryIndex.get(id)

  def previewProgramFor(entry: BePaletteEntry): BeProgram = {
    var tree: Tree[BeBlock, NodeBasedTreePosition] = NodeBasedTreeImpl.empty[BeBlock]()
    val root = tree.rootPosition
    tree = tree.addChild(root, BeBlockFunctionDefinition.starterBlock())
    val bodyPos = tree.rootPosition.forChild(0)
    tree = tree.addChild(bodyPos, entry.instantiate(FunctionBody))
    BeProgram(tree)
  }
}

final case class BePaletteCategory(id: String, label: String, entries: List[BePaletteEntry],
                                   acceptedRoles: Set[BeConnectionRole] = Set(FunctionBody))

final case class BePaletteEntry(id: String, label: String, factory: BeConnectionRole => BeBlock) {
  def instantiate(role: BeConnectionRole): BeBlock = factory(role)
}
