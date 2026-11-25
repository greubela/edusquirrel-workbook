package it.evadid.vm.parsing.source

import upickle.default.ReadWriter

/** Why step 2 could not read a source: the grammar's message and the 1-based position it stopped at. */
final case class SyntaxFailure(message: String, offset: Int, line: Int, column: Int) derives ReadWriter
