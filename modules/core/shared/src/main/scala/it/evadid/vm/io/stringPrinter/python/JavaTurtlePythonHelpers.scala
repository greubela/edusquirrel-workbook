package it.evadid.vm.io.stringPrinter.python

object JavaTurtlePythonHelpers {
  val ModuleName = "java_turtle"

  val installationSource: String = """def _java_turtle_install():
    from builtins import type, int, float, bool, str, list, len, abs, TypeError, ValueError, ZeroDivisionError
    import math
    import sys
    import types

    def integer(value):
        if type(value) is not int or not -2147483648 <= value <= 2147483647:
            raise TypeError("Java int arithmetic needs values in the int range.")
        return value

    def decimal(value):
        if type(value) not in (int, float):
            raise TypeError("Java double arithmetic needs numeric operands.")
        if type(value) is int:
            integer(value)
        return float(value)

    def int32(value):
        return (value + 2147483648) % 4294967296 - 2147483648

    def int_op(operation, left, right=0):
        if type(operation) is not str or operation not in ("add", "sub", "mul", "div", "rem", "pos", "neg"):
            raise ValueError("Choose a supported Java arithmetic operation.")
        left = integer(left)
        if operation == "pos":
            return left
        if operation == "neg":
            return int32(-left)
        right = integer(right)
        if operation == "add":
            return int32(left + right)
        if operation == "sub":
            return int32(left - right)
        if operation == "mul":
            return int32(left * right)
        if operation in ("div", "rem"):
            if right == 0:
                raise ZeroDivisionError("Java integer division by zero.")
            quotient = abs(left) // abs(right)
            if (left < 0) != (right < 0):
                quotient = -quotient
            return int32(quotient if operation == "div" else left - quotient * right)
        raise ValueError("Choose a supported Java arithmetic operation.")

    def double_op(operation, left, right=0):
        if type(operation) is not str or operation not in ("add", "sub", "mul", "div", "rem", "pos", "neg"):
            raise ValueError("Choose a supported Java arithmetic operation.")
        left = decimal(left)
        if operation == "pos":
            return left
        if operation == "neg":
            return -left
        right = decimal(right)
        if operation == "add":
            return left + right
        if operation == "sub":
            return left - right
        if operation == "mul":
            return left * right
        if operation == "div":
            if right == 0.0:
                if left == 0.0 or math.isnan(left):
                    return float("nan")
                return math.copysign(float("inf"), left) * math.copysign(1.0, right)
            return left / right
        if operation == "rem":
            if math.isnan(left) or math.isnan(right) or math.isinf(left) or right == 0.0:
                return float("nan")
            return math.fmod(left, right)
        raise ValueError("Choose a supported Java arithmetic operation.")

    def cell(value):
        if value is not None and type(value) not in (bool, int, float):
            raise TypeError("Use a Java numeric or boolean variable.")
        if type(value) is int:
            integer(value)
        return [value]

    def compare(operation, left, right, floating):
        if type(operation) is not str or operation not in ("lt", "le", "gt", "ge", "eq", "ne"):
            raise ValueError("Choose a supported Java comparison operation.")
        if type(floating) is not bool:
            raise TypeError("Use a boolean Java comparison flag.")
        left = decimal(left) if floating else integer(left)
        right = decimal(right) if floating else integer(right)
        if operation == "lt":
            return left < right
        if operation == "le":
            return left <= right
        if operation == "gt":
            return left > right
        if operation == "ge":
            return left >= right
        if operation == "eq":
            return left == right
        return left != right

    def update(variable, delta, prefix, floating):
        if type(variable) is not list or len(variable) != 1 or type(delta) is not int or delta not in (-1, 1):
            raise TypeError("Increment or decrement a Java variable.")
        if type(prefix) is not bool or type(floating) is not bool:
            raise TypeError("Java updates need boolean prefix and double flags.")
        old = variable[0]
        updated = double_op("add", old, delta) if floating else int_op("add", old, delta)
        variable[0] = updated
        return updated if prefix else old

    module = types.ModuleType("java_turtle")
    module.__all__ = ["cell", "int_op", "double_op", "update", "compare"]
    module.cell = cell
    module.int_op = int_op
    module.double_op = double_op
    module.update = update
    module.compare = compare
    sys.modules["java_turtle"] = module

_java_turtle_install()
del _java_turtle_install
"""
}
