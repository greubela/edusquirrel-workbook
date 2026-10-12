#!/usr/bin/env python3
"""Regression checks for the source inventory; run directly with Python 3."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location(
    "audit", Path(__file__).with_name("audit-scala-serialization.py")
)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class DeclarationInventoryTests(unittest.TestCase):
    def declarations(self, source):
        return audit.declarations("example.scala", source)

    def test_comments_and_literals_do_not_create_declarations(self):
        source = '''/* case class Fake(x: Int) /* sealed trait Fake */ */
// enum Fake
val text = "case class Fake()"
val multiline = """sealed trait Fake
case class Fake()"""
case class Real(value: String)
'''
        self.assertEqual([r["name"] for r in self.declarations(source)], ["Real"])

    def test_multiline_constructor_and_derivation_stay_in_header(self):
        source = '''case class Value[T](
  value: T,
  default: List[Int] = List(1, 2)
) extends Parent[T]
  derives upickle.default.ReadWriter {
  def work = 1
}
'''
        row = self.declarations(source)[0]
        self.assertIn("derives upickle.default.ReadWriter", row["header"])
        self.assertNotIn("def work", row["header"])

    def test_private_constructor_and_numeric_context_are_kept(self):
        source = "final case class Value[T: Fractional] private[example] (value: T) derives ReadWriter"
        self.assertIn("private[example]", self.declarations(source)[0]["header"])
        self.assertTrue(self.declarations(source)[0]["header"].endswith("derives ReadWriter"))

    def test_braces_in_constructor_defaults_do_not_end_the_header(self):
        row = self.declarations("case class Value(value: Int = { val x = 1; x }) derives ReadWriter")[0]
        self.assertTrue(row["header"].endswith("derives ReadWriter"))

    def test_enum_colon_stops_before_cases(self):
        rows = self.declarations("enum Choice derives ReadWriter:\n  case One, Two")
        self.assertEqual(rows[0]["header"], "enum Choice derives ReadWriter")

    def test_adjacent_declarations_have_independent_headers(self):
        rows = self.declarations("sealed trait Root derives ReadWriter\ncase class Child() extends Root derives ReadWriter")
        self.assertEqual(len(rows), 2)
        self.assertNotIn("Child", rows[0]["header"])

    def test_mask_preserves_source_offsets_and_line_numbers(self):
        source = '/* comment\n comment */\ncase class Value(text: String = "escaped\\\"text")'
        self.assertEqual(len(audit.mask(source)), len(source))
        self.assertEqual(self.declarations(source)[0]["line"], 3)


if __name__ == "__main__":
    unittest.main()
