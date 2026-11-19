# Conversion design decision

Programming-state conversions now use the language-neutral `BeExpression` route documented in [programming conversions](programming-conversions.md). Pairwise Java/Python/Snap metadata bridges and the independent Snap XML-to-Python renderer have been removed.

Source frontends own interpretation and validation; target printers own syntax. Unsupported features must be rejected or represented explicitly, never silently dropped or supported through a hidden language-specific node. Checked Java execution and unit-test instrumentation remain separate runtime operations.

Further work should extend shared expressions and their printers together, with cross-language semantic round trips. Do not restore source-specific metadata or duplicate conversion dispatch in UI components.
