# Eva Editor: state-centred programming architecture

`ProgrammingState` is the central access point for Eva Editor functionality. Consumers should ask
the current state for another representation and then invoke behavior owned by that representation:

```scala
EvaEditor.currentState().toBeExpressionState.deriveTurtleCommands
```

They should not reach into an editor implementation, invoke a parser, and pass the parser result to
an unrelated service. Keeping conversion and derived behavior on the state makes the same operation
available regardless of whether Snap, Python, or another editor tab is currently selected.

This change is intentionally a starting point. Snap, Python, and `BeExpression` can convert through
the shared turtle-program subset, and `ProgrammingStateBeExpression` owns synchronous turtle-command
derivation. Java-like source is lowered into the shared VM AST by `JavaToBeExpressionParser`, so Java
states can use the same representation hops. Future SVG, execution, validation, and analysis operations should
follow the same pattern: put them on the representation that can perform them and reach that
representation through `ProgrammingState`.
