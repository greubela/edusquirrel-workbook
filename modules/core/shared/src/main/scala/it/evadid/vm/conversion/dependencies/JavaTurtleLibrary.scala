package it.evadid.vm.conversion.dependencies

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
  /** `goto` is reserved in Java, so Java spells it with its turtle alias `setPosition`. */
  private val reservedSpellings = Map("goto" -> "setPosition")

  /** The Java name of a turtle method, as printed in calls and declared in the library class. */
  def methodName(method: BeDefineFunction): String = {
    val name = method.functionTypeInfo.displayName.getNameIn(English, NamingStyle.CamelCase)
    reservedSpellings.getOrElse(name, name)
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
      |private static void arc(double radius, double extent, boolean left) {
      |    int steps = Math.max(1, (int) Math.ceil(Math.abs(extent) / 10));
      |    double turn = extent / steps * (left ? 1 : -1);
      |    double length = 2 * Math.abs(radius) * Math.sin(Math.toRadians(Math.abs(extent) / steps / 2));
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

  private def indent(text: String): String = text.linesIterator.map(line => if line.isEmpty then line else "    " + line).mkString("\n")

  lazy val source: String = {
    val methods = TurtleCommand.turtleClass.methods.map(method)
    s"class Turtle {\n    // Turtle graphics for running this program on a regular JVM.\n${indent((support +: methods).mkString("\n\n"))}\n}"
  }
}
