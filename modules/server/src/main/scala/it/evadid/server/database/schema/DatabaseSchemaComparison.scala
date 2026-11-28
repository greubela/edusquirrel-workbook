package it.evadid.server.database.schema

/** Pure comparison: no JDBC handles or metadata queries. */
private[database] object DatabaseSchemaComparison {
  import DatabaseSchema.*

  def compare(expected: Table, actual: Table): Seq[String] = {
    val actualColumns = actual.columns.map(column => column.name -> column).toMap
    val columnErrors = expected.columns.flatMap { column =>
      actualColumns.get(column.name) match {
        case None => Seq(s"${expected.name}.${column.name} is missing")
        case Some(found) => compareColumn(expected.name, column, found)
      }
    }
    val extraColumns = (actualColumns.keySet -- expected.columns.map(_.name)).toSeq.sorted
      .map(column => s"${expected.name}.$column is unexpected")
    // Primary keys have their own diagnostic, as in the original inspector.
    val missingUnique = (expected.uniqueKeys -- Set(expected.primaryKey) -- actual.uniqueKeys).toSeq
      .map(key => s"${expected.name} unique key (${key.mkString(",")}) is missing")
    val extraUnique = (actual.uniqueKeys -- expected.uniqueKeys).toSeq
      .map(key => s"${expected.name} has unexpected uniqueness (${key.mkString(",")})")
    val missingForeign = (expected.foreignKeys -- actual.foreignKeys).toSeq
      .map(key => s"${expected.name} foreign key ${key.columns.mkString(",")} differs")
    val extraForeign = (actual.foreignKeys -- expected.foreignKeys).toSeq
      .map(key => s"${expected.name} has unexpected foreign key ${key.columns.mkString(",")}")
    columnErrors ++ extraColumns ++ Option.when(actual.primaryKey != expected.primaryKey)(s"${expected.name} primary key differs") ++
      missingUnique ++ extraUnique ++ missingForeign ++ extraForeign
  }

  private def compareColumn(table: String, expected: Column, actual: Column): Seq[String] = {
    val path = s"$table.${expected.name}"
    Seq(
      Option.when(actual.dataType != expected.dataType || actual.unsigned != expected.unsigned)(s"$path type differs: ${actual.displayType} vs ${expected.displayType}"),
      Option.when(expected.size.exists(size => !actual.size.contains(size)))(s"$path length differs"),
      Option.when(expected.nullable != actual.nullable)(s"$path nullability differs"),
      Option.when(expected.autoIncrement != actual.autoIncrement)(s"$path auto-increment differs"),
      Option.when(textTypes(expected.dataType) && expected.collation.exists(value => !actual.collation.contains(value)))(s"$path collation differs"),
      Option.when(timestampTypes(expected.dataType) && actual.timestampPrecision != expected.timestampPrecision)(s"$path timestamp precision differs"),
      Option.when(expected.dataType == "ENUM" && actual.enumDefinition != expected.enumDefinition)(s"$path enum values differ")
    ).flatten
  }
}
