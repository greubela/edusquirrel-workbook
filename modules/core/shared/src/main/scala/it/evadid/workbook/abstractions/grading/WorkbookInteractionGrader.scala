package it.evadid.workbook.abstractions.grading

import it.evadid.workbook.sync.UpdateImportance
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionFormat}
import it.evadid.workbook.abstractions.grading.GradingStatus.*
import it.evadid.workbook.interactions.gpt.TextInputLlmGrader
import it.evadid.workbook.interactions.programming.programmingExerciseRegular.{ProgramDataTestGrader, ProgrammingUnitTestGrader}
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.{RecreateShapeGrader, TurtleCommandUsageGrader, ProgramStructureGrader}
import it.evadid.workbook.interactions.slideshow.SlideshowGrader
import it.evadid.workbook.interactions.choice.ChoiceGrader
import it.evadid.workbook.interactions.reorderExercise.{ReorderCodeGrader, ReorderMapIdGrader}
import it.evadid.workbook.interactions.table.TableCellGrader
import it.evadid.workbook.interactions.matching.MatchingGrader
import it.evadid.workbook.abstractions.ExecutionContextInformation
import upickle.default.*

import scala.reflect.ClassTag

import scala.concurrent.{ExecutionContext, Future}

trait WorkbookInteractionGrader[T, GA] {
  /** The stable name this grader type is persisted and identified under, independent of its class name. */
  def persistedType: String
  /** The grader's persisted type and parameters; two graders of one type differ by their parameters. */
  protected def identityContent: ujson.Value = this match {
    case product: Product => it.evadid.core.util.crypto.ContentIdentity.typed(persistedType, product)
    case _ => throw IllegalArgumentException(s"$persistedType graders need an explicit identityContent")
  }
  private lazy val contentKey: String = it.evadid.core.util.crypto.ContentIdentity.key(identityContent)
  def key: String = contentKey

  given ec: ExecutionContext = ExecutionContext.global

  private def failWith(state: T, err: Either[String, Throwable]): Future[GradingResult[T, GA]] = err.match {
    case Left(str) => Future.successful(GradingResult[T, GA](this, state, TestError(SerializedException(str))))
    case Right(err) => Future.successful(GradingResult[T, GA](this, state, TestError(SerializedException(err))))
  }

  /** Two independent reasons to run: the caller requested grading (`forceGrading`, e.g. the user's check button),
   * or, unrequested, this grader's `runningBehavior` runs it automatically for an answer of `importance`. */
  def shouldRunGrader(forceGrading: Boolean, importance: UpdateImportance): Boolean =
    forceGrading || runningBehavior.runsAutomatically(importance)

  /** `importance` is the update importance the answer was recorded with. */
  def gradeStateSafe(state: T, forceGrading: Boolean, importance: UpdateImportance, contextInformation: ExecutionContextInformation): Future[GradingResult[T, GA]] =
    try {
      if (shouldRunGrader(forceGrading, importance)) handleGrading(state, contextInformation).recoverWith {
        case err: Throwable => failWith(state, Right(err))
      }
      else Future.successful(GradingResult[T, GA](this, state, NotRequested))
    } catch {
      case (err: Throwable) => failWith(state, Right(err))
      case any: Any => failWith(state, Left(s"Unknown grading error (${any.toString})"))
    }

  protected def handleGrading(state: T, contextInformation: ExecutionContextInformation): Future[GradingResult[T, GA]]

  def importance: GradingImportance

  def runningBehavior: GraderRunningBehavior

  def gradingEntity: FeedbackEntity

}


object WorkbookInteractionGrader {

  /** The codec of one grader type, stored under its persisted name in `serializedType`. */
  final class Codec[G <: WorkbookInteractionGrader[?, ?]](val persistedType: String, val readWriter: ReadWriter[G], val runtimeClass: Class[?])

  def codec[G <: WorkbookInteractionGrader[?, ?]](persistedType: String)(using readWriter: ReadWriter[G], tag: ClassTag[G]): Codec[G] =
    Codec(persistedType, readWriter, tag.runtimeClass)

  /** Reads and writes the graders of `codecs`, tagged with their persisted type. */
  def readWriter[T, A](codecs: Codec[? <: WorkbookInteractionGrader[T, A]]*): ReadWriter[WorkbookInteractionGrader[T, A]] = {
    val byName = codecs.map(codec => codec.persistedType -> codec).toMap
    require(byName.size == codecs.size, s"Grader types must have distinct persisted names: ${codecs.map(_.persistedType)}")
    def codecOf(name: String): Codec[? <: WorkbookInteractionGrader[T, A]] =
      byName.getOrElse(name, throw SerializedException(s"No grader codec registered for '$name' (known: ${byName.keySet})"))
    readwriter[ujson.Value].bimap[WorkbookInteractionGrader[T, A]](
      grader => {
        val codec = codecOf(grader.persistedType).asInstanceOf[Codec[WorkbookInteractionGrader[T, A]]]
        if !codec.runtimeClass.isInstance(grader) then
          throw SerializedException(s"Grader ${grader.persistedType} does not have the type registered under that name")
        val json = writeJs(grader)(using codec.readWriter)
        json.obj.update("serializedType", grader.persistedType)
        json
      },
      json => {
        val name = json.obj.get("serializedType").map(_.str)
          .getOrElse(throw SerializedException("Missing 'serializedType' of a saved grader"))
        read[WorkbookInteractionGrader[T, A]](json)(using codecOf(name).readWriter.asInstanceOf[ReadWriter[WorkbookInteractionGrader[T, A]]])
      })
  }

  /** Every grader type; formats restrict their graders to the types they accept. */
  lazy val knownCodecs: Seq[Codec[? <: WorkbookInteractionGrader[?, ?]]] = List(ChoiceGrader.codec, ReorderCodeGrader.codec,
    ReorderMapIdGrader.codec, TableCellGrader.codec, SlideshowGrader.codec, RecreateShapeGrader.codec, TurtleCommandUsageGrader.codec,
    ProgramStructureGrader.codec, TextInputLlmGrader.codec, ProgrammingUnitTestGrader.codec, ProgramDataTestGrader.codec, MatchingGrader.codec)

  given [T, A]: ReadWriter[WorkbookInteractionGrader[T, A]] =
    readWriter(knownCodecs.map(_.asInstanceOf[Codec[? <: WorkbookInteractionGrader[T, A]]])*)
}
