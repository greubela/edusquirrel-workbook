package it.evadid.server

/** External services may echo credentials, recipients or request data in their exceptions. A guarded call replaces
  * every failure `describe` recognises by an [[IllegalStateException]] with an actionable message and no cause.
  * [[database.SqlFailure]] applies the same rule to JDBC, keeping the SQL state.
  */
object SafeFailure {
  def guard[A](describe: PartialFunction[Throwable, String])(run: => A): A =
    try run
    catch case error if describe.isDefinedAt(error) => throw IllegalStateException(describe(error))
}
