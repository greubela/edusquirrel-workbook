package util.JSXGraph

import scala.scalajs.js

/** A small alias to align with JSXGraph's expectation of fractional numeric types. */
type Fraction[T] = Fractional[T]

/** Converts values of type `T` into JavaScript numbers. */
trait JsValueConverter[T]:
  def toJs(value: T): Double
  def format(value: T): String

object JsValueConverter:
  given defaultConverter[T](using numeric: Fraction[T]): JsValueConverter[T] with
    override def toJs(value: T): Double = numeric.toDouble(value)
    override def format(value: T): String = value.toString

extension [A](value: js.UndefOr[A])
  /** Safe conversion from `js.UndefOr` to Scala's `Option`. */
  def toOption: Option[A] = value.fold[Option[A]](None)(Some(_))
