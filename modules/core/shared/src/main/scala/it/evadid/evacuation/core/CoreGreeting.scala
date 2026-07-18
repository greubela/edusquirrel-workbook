package it.evadid.evacuation.core

object CoreGreeting extends CoreGreeting with App {
  println(greeting)
}

trait CoreGreeting {
  lazy val greeting: String = "hello evacore"
}
