package it.evadid.vm.simulation.java

object JavaInt32 {
  enum Error {
    case DivisionByZero
  }

  def add(left: Int, right: Int): Int = left + right
  def subtract(left: Int, right: Int): Int = left - right
  def multiply(left: Int, right: Int): Int = left * right
  def negate(value: Int): Int = -value

  def divide(left: Int, right: Int): Either[Error, Int] =
    if right == 0 then Left(Error.DivisionByZero) else Right(left / right)

  def remainder(left: Int, right: Int): Either[Error, Int] =
    if right == 0 then Left(Error.DivisionByZero) else Right(left % right)
}
