package it.evadid.vm.editing

import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.tree.{BeExpressionReference, BeExtensionPoint}
import it.evadid.vm.types.BeChildRole.ExpressionInSequence
import it.evadid.vm.types.BeScope.GlobalScope
import it.evadid.vm.types.{BeChildRole, BeDataType}

/** A node's location: the child roles leading from the program root. Roles are unique among siblings. */
final case class BeNodePath(roles: List[BeChildRole]) {
  def /(role: BeChildRole): BeNodePath = BeNodePath(roles :+ role)

  def parent: Option[BeNodePath] = Option.when(roles.nonEmpty)(BeNodePath(roles.init))

  def lastRole: Option[BeChildRole] = roles.lastOption

  /** True for this path and every descendant path. */
  def contains(other: BeNodePath): Boolean = other.roles.startsWith(roles)
}

object BeNodePath {
  val root: BeNodePath = BeNodePath(Nil)
}

/** The insertion point before the statement `index` of the sequence at `sequence`. */
final case class BeSequenceSlot(sequence: BeNodePath, index: Int)

/** Immutable structural edits of a program, addressed by [[BeNodePath]].
  * Every operation returns the edited program or a reason why the edit is not possible.
  */
object BeProgramEditing {

  def children(expression: BeExpression): Seq[(BeChildRole, BeExpression)] =
    expression.structureInfo.getChildrenAsReference(GlobalScope()).map(ref => ref.childInfo.myRoleInParent -> ref.expr)

  def find(program: BeProgram, path: BeNodePath): Option[BeExpression] =
    path.roles.foldLeft(Option(program.fullProgram)) { (current, role) =>
      current.flatMap(children(_).collectFirst { case (`role`, child) => child })
    }

  /** Every statement slot offered by the program's sequences, in source order. */
  def slots(program: BeProgram): Seq[BeSequenceSlot] = {
    def collect(expression: BeExpression, path: BeNodePath): Seq[BeSequenceSlot] = {
      val own = expression match {
        case _: BeSequence => expression.structureInfo.getChildrenAndExtension(GlobalScope()).collect {
          case BeExtensionPoint(_, info, _) => info.myRoleInParent
        }.collect { case ExpressionInSequence(index) => BeSequenceSlot(path, index) }.distinct
        case _ => Nil
      }
      val nested = children(expression).flatMap((role, child) => collect(child, path / role))
      // Slots of a sequence interleave with its statements' own slots.
      (own ++ nested).sortBy(slot => ordering(path, slot))
    }
    collect(program.fullProgram, BeNodePath.root)
  }

  /** Orders slots of one sequence around the nested slots of its statements. */
  private def ordering(owner: BeNodePath, slot: BeSequenceSlot): (Int, Int) =
    if slot.sequence == owner then (slot.index, 0)
    else slot.sequence.roles.drop(owner.roles.size).headOption match {
      case Some(ExpressionInSequence(index)) => (index, 1)
      case _ => (0, 1)
    }

  /** Whether `statement` may be inserted at `slot` (capacity and the sequence's required type). */
  def accepts(program: BeProgram, slot: BeSequenceSlot, statement: BeExpression): Boolean =
    sequenceAt(program, slot.sequence).exists(sequence => acceptance(sequence, slot.index, statement).isEmpty)

  def insert(program: BeProgram, slot: BeSequenceSlot, statement: BeExpression): Either[String, BeProgram] =
    for {
      sequence <- sequenceAt(program, slot.sequence).toRight(s"No sequence at ${slot.sequence}.")
      _ <- acceptance(sequence, slot.index, statement).toLeft(())
      edited <- replace(program, slot.sequence, sequence.copy(body = sequence.body.patch(slot.index, Seq(statement), 0)))
    } yield edited

  /** Removes a statement from its enclosing sequence. */
  def remove(program: BeProgram, path: BeNodePath): Either[String, BeProgram] =
    for {
      (parentPath, index) <- statementPosition(path)
      sequence <- sequenceAt(program, parentPath).toRight(s"$path is not a statement of a sequence.")
      _ <- Either.cond(sequence.body.indices.contains(index), (), s"No statement at $path.")
      edited <- replace(program, parentPath, sequence.copy(body = sequence.body.patch(index, Nil, 1)))
    } yield edited

  /** Moves a statement to another slot; the slot refers to the program before the move. */
  def move(program: BeProgram, from: BeNodePath, to: BeSequenceSlot): Either[String, BeProgram] =
    for {
      statement <- find(program, from).toRight(s"No statement at $from.")
      _ <- Either.cond(!from.contains(to.sequence), (), "A statement cannot be moved into itself.")
      (fromSequence, fromIndex) <- statementPosition(from)
      removed <- remove(program, from)
      target = adjustedSlot(to, fromSequence, fromIndex)
      inserted <- insert(removed, target, statement)
    } yield inserted

  /** Replaces the node at `path`; the root is replaced by an empty path. */
  def replace(program: BeProgram, path: BeNodePath, replacement: BeExpression): Either[String, BeProgram] = {
    def rebuild(current: BeExpression, roles: List[BeChildRole]): Either[String, BeExpression] = roles match {
      case Nil => Right(replacement)
      case role :: rest =>
        for {
          child <- children(current).collectFirst { case (`role`, child) => child }.toRight(s"No child $role.")
          newChild <- rebuild(child, rest)
          rebuilt = current.structureInfo.withReplacedChildren(Map(role -> newChild))
          _ <- Either.cond(children(rebuilt).exists((r, c) => r == role && c == newChild), (),
            s"${current.getClass.getSimpleName} cannot take this value as $role.")
        } yield rebuilt
    }
    rebuild(program.fullProgram, path.roles).map(BeProgram(_))
  }

  private def sequenceAt(program: BeProgram, path: BeNodePath): Option[BeSequence] =
    find(program, path).collect { case sequence: BeSequence => sequence }

  private def statementPosition(path: BeNodePath): Either[String, (BeNodePath, Int)] =
    (path.parent, path.lastRole) match {
      case (Some(parent), Some(ExpressionInSequence(index))) => Right(parent -> index)
      case _ => Left(s"$path is not a statement of a sequence.")
    }

  private def acceptance(sequence: BeSequence, index: Int, statement: BeExpression): Option[String] = {
    val required = sequence.sequenceInfo.mustEvaluateTo
    if index < 0 || index > sequence.body.size then Some(s"Slot $index is outside the sequence.")
    else if sequence.sequenceInfo.maxBodyElements.exists(sequence.body.size >= _) then Some("The sequence is full.")
    else if required.exists(!acceptsType(_, statement)) then Some(s"Expected a value of type ${required.get}.")
    else None
  }

  private def acceptsType(required: BeDataType, statement: BeExpression): Boolean =
    required.canTakeValuesFrom(statement.staticInformationExpression.staticType).possibleWithoutSyntaxErrors

  /** Removing statement `index` of `sequence` shifts later siblings, and every path through them, by one. */
  private def adjustedSlot(slot: BeSequenceSlot, sequence: BeNodePath, index: Int): BeSequenceSlot =
    if slot.sequence == sequence then
      if slot.index > index then slot.copy(index = slot.index - 1) else slot
    else if sequence.contains(slot.sequence) then
      slot.sequence.roles.drop(sequence.roles.size) match {
        case ExpressionInSequence(nr) :: rest if nr > index =>
          slot.copy(sequence = BeNodePath(sequence.roles ++ (ExpressionInSequence(nr - 1) :: rest)))
        case _ => slot
      }
    else slot
}
