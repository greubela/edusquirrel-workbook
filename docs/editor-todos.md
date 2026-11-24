# Editor follow-ups

- Java function flowcharts: add an asynchronous source-position-preserving Java-to-BeExpression flow plan. Keep the gutter hidden until it describes the actual function; do not render Python or recovered-source approximations.
- Nested Python declarations: outer graphs stay hidden where a nested definition interrupts the source region. Inner function graphs remain independent.
- Diagnostic gutter: first carry typed source ranges through both parsers and BeExpression conversion. Then render localized `LanguageMap` warnings for syntax errors, unsupported expressions and unknown dependencies. Conversion errors alone cannot accurately locate all three cases; do not guess positions by searching for repeated source fragments.
