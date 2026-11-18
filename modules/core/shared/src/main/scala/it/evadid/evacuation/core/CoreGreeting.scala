package it.evadid.evacuation.core

object CoreGreeting extends CoreGreeting {
  def main(args: Array[String]): Unit = println(greeting)
}

trait CoreGreeting {
  lazy val greeting: String = "hello evacore"
}
