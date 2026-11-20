package it.evadid.homepage.webElements.editor.blocks

import it.evadid.core.datastructures.geometry.{Bounds, Point}
import it.evadid.core.datastructures.vectorShapes.blocks.BlockRole
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.editing.{BeNodePath, BeProgramEditing, BeSequenceSlot}

/** What a pointer press picked up: a library template or a statement of the program. */
enum DragSource {
  case Template(statement: BeExpression)
  case Moving(path: BeNodePath, statement: BeExpression)

  def statement: BeExpression
}

/** A pointer press in client coordinates; it becomes a drag once it moved past the threshold.
  * `overProgram` is the pointer in the program drawing's coordinates while it is above the drawing.
  */
final case class Press(source: Option[DragSource], value: Option[BeNodePath], start: Point[Double], current: Point[Double],
                       active: Boolean, overProgram: Option[Point[Double]]) {
  def movedTo(point: Point[Double], inProgram: Option[Point[Double]]): Press =
    copy(current = point, overProgram = inProgram,
      active = active || (source.nonEmpty && point.distanceToSquared(start) > BlockDragging.threshold * BlockDragging.threshold))
}

/** Hit testing and drop decisions; pure functions of the program and its laid-out roles. */
object BlockDragging {
  val threshold = 4.0

  private def contains(bounds: Bounds[Double], point: Point[Double]) =
    point.x >= bounds.startX && point.x <= bounds.endX && point.y >= bounds.startY && point.y <= bounds.endY

  /** Roles are listed outer first, so the last containing role is the innermost. */
  private def innermost[T](roles: Seq[(BlockRole, Bounds[Double])], point: Point[Double])(pick: PartialFunction[BlockRole, T]): Option[T] =
    roles.filter((_, bounds) => contains(bounds, point)).map(_._1).collect(pick).lastOption

  /** The statement and value under a press in the program drawing. */
  def pressAt(program: BeProgram, roles: Seq[(BlockRole, Bounds[Double])], point: Point[Double]): (Option[DragSource], Option[BeNodePath]) = {
    val statement = innermost(roles, point) { case BlockRole.Statement(path) => path }
      .flatMap(path => BeProgramEditing.find(program, path).map(DragSource.Moving(path, _)))
    (statement, innermost(roles, point) { case BlockRole.Value(path) => path })
  }

  /** Slots that would take the dragged statement; a statement cannot move into itself. */
  def accepted(program: BeProgram, source: DragSource): Set[BeSequenceSlot] =
    BeProgramEditing.slots(program).filter { slot =>
      BeProgramEditing.accepts(program, slot, source.statement) && (source match {
        case DragSource.Moving(path, _) => !path.contains(slot.sequence)
        case DragSource.Template(_) => true
      })
    }.toSet

  /** The accepted slot closest to the pointer, as the legacy editor chose its drop target. */
  def nearest(roles: Seq[(BlockRole, Bounds[Double])], accepted: Set[BeSequenceSlot], point: Point[Double]): Option[BeSequenceSlot] =
    roles.collect { case (BlockRole.Slot(slot), bounds) if accepted(slot) => slot -> bounds.centerPoint.distanceToSquared(point) }
      .minByOption(_._2).map(_._1)

  def drop(program: BeProgram, source: DragSource, slot: BeSequenceSlot): Either[String, BeProgram] = source match {
    case DragSource.Template(statement) => BeProgramEditing.insert(program, slot, statement)
    case DragSource.Moving(path, _) => BeProgramEditing.move(program, path, slot)
  }

  /** Dropping a program statement on the library removes it. */
  def discard(program: BeProgram, source: DragSource): Either[String, BeProgram] = source match {
    case DragSource.Moving(path, _) => BeProgramEditing.remove(program, path)
    case DragSource.Template(_) => Right(program)
  }
}
