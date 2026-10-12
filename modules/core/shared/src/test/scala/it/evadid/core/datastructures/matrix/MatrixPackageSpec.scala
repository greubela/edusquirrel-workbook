package it.evadid.core.datastructures.matrix

import munit.FunSuite

class MatrixPackageSpec extends FunSuite {
  private val dim = MatrixDimension(3, 2)
  private val matrix = Matrix(dim, List(0, 1, 2, 3, 4, 5))

  test("MatrixDimension enumerates rows and columns in the advertised order") {
    assertEquals(dim.positions.flatMap(_.asIndex), 0.until(6))
    assertEquals(dim.transposedPositions.flatMap(_.asIndex), Seq(0, 3, 1, 4, 2, 5))
    assertEquals(dim.getRowPositions(1, 1).map(_.cPos), Seq(MatrixPosition(1, 1), MatrixPosition(2, 1)))
    assertEquals(dim.getColPositions(2).map(_.cPos), Seq(MatrixPosition(2, 0), MatrixPosition(2, 1)))
    assertEquals(dim.transposed, MatrixDimension(2, 3))
    assertEquals(dim.addRow().addCol(), MatrixDimension(4, 3))
    assertEquals(MatrixDimension(257, 258).encodeToInt(), 0x01010102)
    intercept[AssertionError](MatrixDimension(0, 1))
    intercept[AssertionError](MatrixDimension(1, -1))
    intercept[AssertionError](MatrixDimension(Short.MaxValue, 1).encodeToInt())
  }

  test("MatrixPosition arithmetic, distances and rectangle generation") {
    val p = MatrixPosition(2, 3)
    assertEquals(p.add(MatrixPosition(3, 4)), MatrixPosition(5, 7))
    assertEquals(p.sub(MatrixPosition(3, 4)), MatrixPosition(-1, -1))
    assertEquals(p.mult(-2), MatrixPosition(-4, -6))
    assertEquals(p.transposed, MatrixPosition(3, 2))
    assertEquals(p.inDirection(Direction.TOP_LEFT), MatrixPosition(1, 2))
    assertEqualsDouble(p.euclidianDistTo(MatrixPosition(5, 7)), 5.0, 1e-10)
    assertEquals(p.manhattenDistTo(MatrixPosition(-1, -1)).intValue, 7)
    assertEqualsDouble(p.pDistTo(MatrixPosition(5, 7), 2), 5.0, 1e-10)
    assertEquals(MatrixPosition.getRectangle(2, 2), Seq(MatrixPosition(0, 0), MatrixPosition(0, 1), MatrixPosition(1, 0), MatrixPosition(1, 1)))
    assertEquals(MatrixPosition.getRectangle(0, 2), Seq.empty[MatrixPosition])
  }

  test("PositionInMatrix rejects off-grid positions and canonicalizes wrapped coordinates") {
    val last = PositionInMatrix(5, dim)
    assertEquals(last.cPos, MatrixPosition(2, 1))
    assertEquals(last.asIndex, Some(5))
    assertEquals(last.getFrom(matrix), Some(5))
    assertEquals(last.getFromOrFail(matrix), 5)
    val outside = MatrixPosition(-1, 2).in(dim)
    assert(!outside.isInRange)
    assertEquals(outside.asIndex, None)
    assertEquals(outside.getFrom(matrix), None)
    intercept[NoSuchElementException](outside.getFromOrFail(matrix))
    val wrapped = MatrixPosition(-4, 5).in(dim.copy(wrapAround = true))
    assertEquals(wrapped.cPos, MatrixPosition(2, 1))
    assertEquals(wrapped.asIndex, Some(5))
    assertEquals(wrapped, PositionInMatrix(MatrixPosition(2, 1), wrapped.dim))
    assertEquals(wrapped.hashCode, PositionInMatrix(MatrixPosition(2, 1), wrapped.dim).hashCode)
  }

  test("Direction lookup supports aliases and rejects unknown directions") {
    val aliases = Map("UP" -> Direction.TOP, "down" -> Direction.BOTTOM, "left" -> Direction.LEFT,
      "right" -> Direction.RIGHT, "upleft" -> Direction.TOP_LEFT, "topright" -> Direction.TOP_RIGHT,
      "downleft" -> Direction.BOTTOM_LEFT, "bottomright" -> Direction.BOTTOM_RIGHT)
    aliases.foreach((name, direction) => assertEquals(Direction.fromString(name), Some(direction)))
    Direction.mooreDirections.foreach(d => assertEquals(Direction.fromPosition(d.toPosition), Some(d)))
    assertEquals(Direction.fromString("sideways"), None)
    assertEquals(Direction.fromPosition(MatrixPosition(0, 0)), None)
    assertEquals(Direction.neumannDirections.size, 4)
    assertEquals(Direction.diagonal.toSet.intersect(Direction.neumannDirections.toSet), Set.empty[Direction])
  }

  test("Neighbourhood definitions clip at borders and deduplicate wrapped neighbours") {
    assertEquals(Neighbourhood.moore.function.toSet.size, 8)
    assertEquals(Neighbourhood.neumann.function.toSet, Direction.neumannDirections.map(_.toPosition).toSet)
    assert(Neighbourhood.knight.function.forall(p => math.abs(p.x) + math.abs(p.y) == 3))
    assertEquals(matrix.getNeighbours(MatrixPosition(0, 0), Neighbourhood.neumann.function), Set(1, 3))
    assertEquals(matrix.getNeighbourPositions(MatrixPosition(0, 0), Neighbourhood.moore.function).map(_.cPos),
      Set(MatrixPosition(1, 0), MatrixPosition(0, 1), MatrixPosition(1, 1)))
    val wrapped = MatrixPosition(0, 0).in(MatrixDimension(1, 1, true))
    assertEquals(wrapped.neighbours(Neighbourhood.moore.function), Set(wrapped))
    assertEquals(Neighbourhood("custom", Seq(MatrixPosition(2, 0))).function, Seq(MatrixPosition(2, 0)))
  }

  test("Matrix access, immutable replacement, mapping and non-square transposition") {
    assertEquals(matrix.get(MatrixPosition(2, 1)), Some(5))
    assertEquals(matrix.get(MatrixPosition(3, 1)), None)
    assertEquals(matrix.replace(MatrixPosition(1, 0), 9).elements, List(0, 9, 2, 3, 4, 5))
    assertEquals(matrix.elements, List(0, 1, 2, 3, 4, 5))
    assertEquals(matrix.replace(MatrixPosition(-1, 0), 9), matrix)
    assertEquals(matrix.mapTiles(_.toString).elements, List("0", "1", "2", "3", "4", "5"))
    assertEquals(matrix.transposed.elements, List(0, 3, 1, 4, 2, 5))
    assertEquals(matrix.transposed.transposed, matrix)
    assertEquals(Matrix(dim, p => p.asIndex.get), matrix)
    assertEquals(matrix.elementsAtPosition.map(_._1), matrix.elements)
  }

  test("Matrix inserts and removes interior rows and columns") {
    val row = matrix.addRow(p => 10 + p.x, 1)
    assertEquals(row.elements, List(0, 1, 2, 10, 11, 12, 3, 4, 5))
    assertEquals(row.removeRow(1), matrix)
    val column = matrix.addColumn(p => 10 + p.y, 1)
    assertEquals(column.elements, List(0, 10, 1, 2, 3, 11, 4, 5))
    assertEquals(column.removeColumn(1), matrix)
    assertEquals(matrix.addRow(_ => 9).removeRow(), matrix)
    assertEquals(matrix.addColumn(_ => 9).removeColumn(), matrix)
    intercept[AssertionError](matrix.addRow(_ => 9, -1))
    intercept[AssertionError](matrix.removeColumn(3))
    intercept[AssertionError](Matrix(MatrixDimension(1, 1), List(0)).removeRow())
  }

  test("Matrix resizing preserves overlap and calls the factory only for new positions") {
    var generated = Set.empty[MatrixPosition]
    val bigger = matrix.setToDimension(MatrixDimension(4, 3), p => { generated += p; 9 })
    assertEquals(bigger.elements, List(0, 1, 2, 9, 3, 4, 5, 9, 9, 9, 9, 9))
    assertEquals(generated.size, 6)
    assertEquals(bigger.setToDimension(dim, _ => fail("unexpected factory call")), matrix)
    assertEquals(matrix.setToDimension(dim, _ => fail("unexpected factory call")), matrix)
  }
}
