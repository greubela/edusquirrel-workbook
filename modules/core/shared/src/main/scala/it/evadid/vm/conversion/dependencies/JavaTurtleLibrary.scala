package it.evadid.vm.conversion.dependencies

import it.evadid.vm.conversion.structured.SourceStructure.indent
import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.defining.BeDefineFunction
import it.evadid.vm.naming.NamingStyle
import it.evadid.vm.types.BeDataType

/** The Java `Turtle` class that lets printed Java programs run on a regular JVM.
  *
  * Its static methods are generated from the shared turtle signatures, so they match the calls the
  * Java printer emits (`Turtle.forward(…)`). Movement follows Python's turtle: heading 0 points
  * right, positive angles turn left and y points up. Segments are drawn in a Swing window unless the
  * JVM is headless; `segments()` returns them as `{x1, y1, x2, y2}`.
  */
object JavaTurtleLibrary {
  /** Java spellings that differ from the canonical names, all of them turtle aliases: the Java turtle
    * API (as in the checked Java exercise) turns with `turnLeft`/`turnRight`, and `goto` is reserved.
    */
  private val javaSpellings = Map("left" -> "turnLeft", "right" -> "turnRight", "goto" -> "setPosition")

  /** The Java name of a turtle method, as printed in calls and declared in the library class. */
  def methodName(method: BeDefineFunction): String = {
    val name = method.functionTypeInfo.displayName.getNameIn(English, NamingStyle.CamelCase)
    javaSpellings.getOrElse(name, name)
  }

  private def javaType(dataType: BeDataType): String =
    if dataType == BeDataType.Boolean then "boolean" else if dataType == BeDataType.String then "String" else "double"

  /** Statements for the turtle methods that move or turn; the others only change settings. */
  private def body(method: BeDefineFunction): String = {
    val inputs = method.inputs.map(_.name.getNameIn(English, NamingStyle.CamelCase))
    method.functionTypeInfo.displayName.universalInterpretation() match {
      case "forward" => s"move(${inputs.head});"
      case "backward" => s"move(-${inputs.head});"
      case "left" => s"heading += ${inputs.head};"
      case "right" => s"heading -= ${inputs.head};"
      case "pen_up" => "penIsDown = false;"
      case "pen_down" => "penIsDown = true;"
      case "goto" => s"moveTo(${inputs(0)}, ${inputs(1)});"
      case "setx" => s"moveTo(${inputs.head}, posY);"
      case "sety" => s"moveTo(posX, ${inputs.head});"
      case "setheading" => s"heading = ${inputs.head};"
      case "home" => "moveTo(0, 0);\n    heading = 0;"
      case "towards" => s"heading = Math.toDegrees(Math.atan2(${inputs(1)} - posY, ${inputs(0)} - posX));"
      case "circle" => s"arc(${inputs.head}, 360, true);"
      case "arc_left" => s"arc(${inputs(0)}, ${inputs(1)}, true);"
      case "arc_right" => s"arc(${inputs(0)}, ${inputs(1)}, false);"
      case "clear" => "segments.clear();\n    repaint();"
      case "reset" => "segments.clear();\n    posX = 0;\n    posY = 0;\n    heading = 0;\n    penIsDown = true;\n    repaint();"
      case _ => "// Pen, speed and embroidery settings do not change the drawn path."
    }
  }

  private def method(method: BeDefineFunction): String = {
    val parameters = method.inputs.map(input => s"${javaType(input.variableType)} ${input.name.getNameIn(English, NamingStyle.CamelCase)}").mkString(", ")
    s"public static void ${methodName(method)}($parameters) {\n    ${body(method)}\n}"
  }

  private val support =
    """private static final java.util.List<double[]> segments = new java.util.concurrent.CopyOnWriteArrayList<>();
      |private static double posX = 0;
      |private static double posY = 0;
      |private static double heading = 0;
      |private static boolean penIsDown = true;
      |private static javax.swing.JFrame window;
      |
      |/** The drawn segments as {x1, y1, x2, y2}, with y pointing up. */
      |public static java.util.List<double[]> segments() {
      |    return segments;
      |}
      |
      |private static void moveTo(double x, double y) {
      |    if (penIsDown) segments.add(new double[] {posX, posY, x, y});
      |    posX = x;
      |    posY = y;
      |    repaint();
      |}
      |
      |private static void move(double distance) {
      |    double radians = Math.toRadians(heading);
      |    moveTo(posX + distance * Math.cos(radians), posY + distance * Math.sin(radians));
      |}
      |
      |/** As Python's turtle.circle (and the shared TurtlePathBuilder.arcSteps): chords with a half turn at both ends. */
      |private static void arc(double radius, double extent, boolean left) {
      |    int steps = 1 + (int) (Math.min(11 + Math.abs(radius) / 6.0, 59.0) * Math.abs(extent) / 360.0);
      |    double turn = extent / steps;
      |    double length = 2 * radius * Math.sin(Math.toRadians(turn / 2));
      |    if (radius < 0) { length = -length; turn = -turn; }
      |    if (!left) turn = -turn;
      |    heading += turn / 2;
      |    for (int step = 0; step < steps; step++) {
      |        move(length);
      |        heading += turn;
      |    }
      |    heading -= turn / 2;
      |}
      |
      |private static void repaint() {
      |    if (java.awt.GraphicsEnvironment.isHeadless()) return;
      |    if (window == null) {
      |        window = new javax.swing.JFrame("Turtle");
      |        window.setDefaultCloseOperation(javax.swing.JFrame.DISPOSE_ON_CLOSE);
      |        window.add(new javax.swing.JPanel() {
      |            @Override
      |            protected void paintComponent(java.awt.Graphics graphics) {
      |                super.paintComponent(graphics);
      |                double centerX = getWidth() / 2.0;
      |                double centerY = getHeight() / 2.0;
      |                for (double[] segment : segments) {
      |                    graphics.drawLine((int) Math.round(centerX + segment[0]), (int) Math.round(centerY - segment[1]),
      |                        (int) Math.round(centerX + segment[2]), (int) Math.round(centerY - segment[3]));
      |                }
      |            }
      |        });
      |        window.setSize(800, 600);
      |        window.setVisible(true);
      |    }
      |    window.repaint();
      |}""".stripMargin

  lazy val source: String = withMethods(Nil)

  private def withMethods(extra: Seq[String]): String = {
    val methods = TurtleCommand.turtleClass.methods.map(method) ++ extra
    s"class Turtle {\n    // Turtle graphics for running this program on a regular JVM.\n${indent((support +: methods).mkString("\n\n"))}\n}"
  }

  /** The library for a program that leaves the workbook: `source` plus forwarding methods for the turtle
    * spellings the program calls that the class does not declare (the workbook accepts `Turtle.right`,
    * `Turtle.fd`, … as aliases). Kept apart from `source`, which printers and parsers match verbatim.
    */
  def exportSource(calledNames: Set[String]): String = {
    val declared = TurtleCommand.turtleClass.methods.map(methodName).toSet
    val aliases = calledNames.toSeq.sorted.filterNot(declared).flatMap { alias =>
      TurtleCommand.resolveTurtleMethod(alias).map { target =>
        val inputs = target.inputs.map(_.name.getNameIn(English, NamingStyle.CamelCase))
        val parameters = target.inputs.zip(inputs).map((input, name) => s"${javaType(input.variableType)} $name").mkString(", ")
        s"public static void $alias($parameters) {\n    ${methodName(target)}(${inputs.mkString(", ")});\n}"
      }
    }
    withMethods(aliases)
  }
}
