For a feature or bug fix, compile the affected modules and run the relevant unit tests before finishing. Use the named sbt tasks documented in [README.md](../../README.md).

The complete Scala check is:

```sh
sbt 'coreJVM/test' 'coreJS/test' 'client/test' 'server/test' 'worker/test'
```

Use `buildClientDev` and `buildWorkerDev` when browser checks need rebuilt artifacts. The README lists static and Playwright checks. For documentation-only edits, validate local links and compile new Scala examples against the current APIs.
