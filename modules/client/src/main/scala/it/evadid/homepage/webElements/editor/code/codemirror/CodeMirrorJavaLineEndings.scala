package it.evadid.homepage.webElements.editor.code.codemirror

import scala.collection.mutable
import scala.scalajs.js

private[code] object CodeMirrorJavaLineEndings {
  import CodeMirrorApi.*

  final case class Exception(pos: Int, separator: String)
  final case class Metadata(separator: String, exceptions: Vector[Exception])

  private val separators = "\r\n|\r|\n".r

  def parse(source: String): Metadata = {
    val separator = separators.findFirstIn(source).getOrElse("\n")
    val exceptions = Vector.newBuilder[Exception]
    var removed = 0
    separators.findAllMatchIn(source).foreach { matched =>
      if matched.matched != separator then exceptions += Exception(matched.start - removed, matched.matched)
      removed += matched.matched.length - 1
    }
    Metadata(separator, exceptions.result())
  }

  def text(source: String): Text =
    Libraries.Text.of(js.Array(source.split("\r\n|\r|\n", -1)*))

  private def map(exceptions: Vector[Exception], changes: Changes): Vector[Exception] =
    exceptions.flatMap { entry =>
      val position = changes.mapPos(entry.pos, 1, Libraries.MapMode.TrackAfter)
      if position == null then None else Some(entry.copy(pos = position.asInstanceOf[Int]))
    }

  private lazy val restore: EffectType[Vector[Exception]] = Libraries.StateEffect.define[Vector[Exception]](
    js.Dynamic.literal(map = ((value: Vector[Exception], changes: Changes) => {
      val mapped = map(value, changes)
      if mapped.isEmpty then js.undefined else mapped
    }): js.Function2[Vector[Exception], Changes, js.UndefOr[Vector[Exception]]])
  )

  lazy val reset: EffectType[Metadata] = Libraries.StateEffect.define[Metadata]()

  lazy val field: Field[Metadata] = Libraries.StateField.define[Metadata](
    js.Dynamic.literal(
      create = (() => Metadata("\n", Vector.empty)): js.Function0[Metadata],
      update = ((value: Metadata, transaction: Transaction) => {
        var next = if transaction.docChanged then value.copy(exceptions = map(value.exceptions, transaction.changes)) else value
        var patches = Option.empty[mutable.Map[Int, String]]
        transaction.effects.foreach { effect =>
          if effect.is(reset) then {
            next = effect.value.asInstanceOf[Metadata]
            patches = None
          } else if effect.is(restore) then {
            val current = patches.getOrElse {
              val initialized = mutable.Map.from(next.exceptions.map(entry => entry.pos -> entry.separator))
              patches = Some(initialized)
              initialized
            }
            effect.value.asInstanceOf[Vector[Exception]].foreach { entry =>
              if transaction.newDoc.sliceString(entry.pos, entry.pos + 1) == "\n" then
                if entry.separator == next.separator then current.remove(entry.pos)
                else current.update(entry.pos, entry.separator)
            }
          }
        }
        patches.fold(next)(current => next.copy(exceptions = current.toVector.sortBy(_._1).map {
          case (position, separator) => Exception(position, separator)
        }))
      }): js.Function2[Metadata, Transaction, Metadata]
    )
  )

  lazy val historyEffects: Extension = Libraries.invertedEffects.of(
    ((transaction: Transaction) => {
      val before = transaction.startState.field(field)
      val restored = mutable.LinkedHashMap.empty[Int, String]
      var index = 0
      transaction.changes.iterChangedRanges((from: Int, to: Int, _: Int, _: Int) => {
        while index < before.exceptions.length && before.exceptions(index).pos < from do index += 1
        while index < before.exceptions.length && before.exceptions(index).pos < to do {
          val entry = before.exceptions(index)
          index += 1
          restored.update(entry.pos, entry.separator)
        }
      })
      val effects = transaction.effects.filter(_.is(restore))
      if effects.nonEmpty then {
        val inverse = transaction.changes.invertedDesc
        val exceptions = before.exceptions.map(entry => entry.pos -> entry.separator).toMap
        effects.foreach { effect =>
          effect.value.asInstanceOf[Vector[Exception]].foreach { entry =>
            val position = inverse.mapPos(entry.pos, 1, Libraries.MapMode.TrackAfter)
            if position != null then {
              val pos = position.asInstanceOf[Int]
              if transaction.startState.doc.sliceString(pos, pos + 1) == "\n" then
                restored.update(pos, exceptions.getOrElse(pos, before.separator))
            }
          }
        }
      }
      if restored.isEmpty then js.Array[js.Object]()
      else js.Array[js.Object](restore.of(restored.iterator.map { case (position, separator) => Exception(position, separator) }.toVector))
    }): js.Function1[Transaction, js.Array[js.Object]]
  )

  def source(state: State): String = {
    val normalized = state.doc.toString()
    val endings = state.field(field)
    val restored = new StringBuilder
    var index = 0
    var start = 0
    normalized.indices.foreach { pos =>
      if normalized.charAt(pos) == '\n' then {
        restored.append(normalized.substring(start, pos))
        if endings.exceptions.lift(index).exists(_.pos == pos) then {
          restored.append(endings.exceptions(index).separator)
          index += 1
        } else restored.append(endings.separator)
        start = pos + 1
      }
    }
    restored.append(normalized.substring(start)).toString
  }
}
