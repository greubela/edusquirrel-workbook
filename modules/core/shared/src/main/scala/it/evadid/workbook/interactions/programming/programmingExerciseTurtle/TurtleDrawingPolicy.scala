package it.evadid.workbook.interactions.programming.programmingExerciseTurtle

import upickle.default.{ReadWriter, readwriter}

enum TurtleDrawingPolicy {
  case Segments, Strokes, Coverage
}

object TurtleDrawingPolicy {
  given ReadWriter[TurtleDrawingPolicy] = readwriter[ujson.Value].bimap[TurtleDrawingPolicy](
    policy => ujson.Str(policy.toString),
    {
      case ujson.Str(name) => values.find(_.toString == name)
        .getOrElse(throw IllegalArgumentException(s"Unknown turtle comparison policy: $name"))
      case _ => throw IllegalArgumentException("Expected a turtle comparison policy.")
    }
  )
}
