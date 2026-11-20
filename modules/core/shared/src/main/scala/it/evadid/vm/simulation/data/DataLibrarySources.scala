package it.evadid.vm.simulation.data

/**
 * Stand-alone Java and Python implementations of the `NumberList` and `Sudoku` libraries, for programs
 * that leave the workbook (downloads). They hold the given data and behave like the interpreter's
 * libraries: positions start at 0, empty sudoku cells are 0 and given numbers cannot be changed.
 */
object DataLibrarySources {

  def javaNumberList(values: Seq[Int]): String =
    s"""/**
       | * The list of numbers from the workbook. Positions start at 0.
       | */
       |public class NumberList {
       |    private static int[] values = {${values.mkString(", ")}};
       |
       |    public static int get(int index) {
       |        return values[index];
       |    }
       |
       |    public static void set(int index, int value) {
       |        values[index] = value;
       |    }
       |
       |    public static void swap(int first, int second) {
       |        int value = values[first];
       |        values[first] = values[second];
       |        values[second] = value;
       |    }
       |
       |    public static int length() {
       |        return values.length;
       |    }
       |
       |    /** Prints the list, e.g. [1, 3, 5]. */
       |    public static void print() {
       |        System.out.println(java.util.Arrays.toString(values));
       |    }
       |}
       |""".stripMargin

  /** Rows of a grid, keeping only the cells selected by `keep` (others become 0). */
  private def rows(grid: SudokuGrid, open: String, close: String, keep: Int => Boolean = _ => true): String =
    grid.cells.indices.map(index => if keep(index) then grid.cells(index) else 0).grouped(9)
      .map(_.mkString(open, ", ", close)).mkString(",\n        ")

  def javaSudoku(grid: SudokuGrid): String = {
    s"""/**
       | * The sudoku from the workbook. Rows and columns go from 0 to 8; 0 marks an empty cell.
       | * The puzzle's given numbers cannot be changed.
       | */
       |public class Sudoku {
       |    private static final int[][] GIVEN = {
       |        ${rows(grid, "{", "}", grid.givens)}
       |    };
       |    private static int[][] cells = {
       |        ${rows(grid, "{", "}")}
       |    };
       |
       |    public static int get(int row, int column) {
       |        return cells[row][column];
       |    }
       |
       |    public static void set(int row, int column, int value) {
       |        if (value < 1 || value > 9) {
       |            throw new IllegalArgumentException("A sudoku cell holds 1 to 9, not " + value + ".");
       |        }
       |        requireChangeable(row, column);
       |        cells[row][column] = value;
       |    }
       |
       |    public static void remove(int row, int column) {
       |        requireChangeable(row, column);
       |        cells[row][column] = 0;
       |    }
       |
       |    /** Prints the grid with . for empty cells. */
       |    public static void print() {
       |        for (int[] row : cells) {
       |            StringBuilder line = new StringBuilder();
       |            for (int value : row) {
       |                line.append(value == 0 ? "." : String.valueOf(value)).append(' ');
       |            }
       |            System.out.println(line.toString().trim());
       |        }
       |    }
       |
       |    private static void requireChangeable(int row, int column) {
       |        if (GIVEN[row][column] != 0) {
       |            throw new IllegalArgumentException("Row " + row + ", column " + column + " holds a given number and cannot be changed.");
       |        }
       |    }
       |}
       |""".stripMargin
  }

  def pythonNumberList(values: Seq[Int]): String =
    s"""class NumberList:
       |    \"\"\"The list of numbers from the workbook. Positions start at 0.\"\"\"
       |    values = [${values.mkString(", ")}]
       |
       |    @staticmethod
       |    def get(index):
       |        return NumberList.values[index]
       |
       |    @staticmethod
       |    def set(index, value):
       |        NumberList.values[index] = value
       |
       |    @staticmethod
       |    def swap(first, second):
       |        values = NumberList.values
       |        values[first], values[second] = values[second], values[first]
       |
       |    @staticmethod
       |    def length():
       |        return len(NumberList.values)
       |""".stripMargin

  def pythonSudoku(grid: SudokuGrid): String = {
    s"""class Sudoku:
       |    \"\"\"The sudoku from the workbook. Rows and columns go from 0 to 8; 0 marks an empty cell.\"\"\"
       |    given = [
       |        ${rows(grid, "[", "]", grid.givens)}
       |    ]
       |    cells = [
       |        ${rows(grid, "[", "]")}
       |    ]
       |
       |    @staticmethod
       |    def get(row, column):
       |        return Sudoku.cells[row][column]
       |
       |    @staticmethod
       |    def set(row, column, value):
       |        if not 1 <= value <= 9:
       |            raise ValueError(f"A sudoku cell holds 1 to 9, not {value}.")
       |        Sudoku._require_changeable(row, column)
       |        Sudoku.cells[row][column] = value
       |
       |    @staticmethod
       |    def remove(row, column):
       |        Sudoku._require_changeable(row, column)
       |        Sudoku.cells[row][column] = 0
       |
       |    @staticmethod
       |    def _require_changeable(row, column):
       |        if Sudoku.given[row][column] != 0:
       |            raise ValueError(f"Row {row}, column {column} holds a given number and cannot be changed.")
       |""".stripMargin
  }
}
