# Shared-core test guide

Shared domain behavior runs on both JVM and Scala.js. Keep tests alongside the
matching production package under `modules/core/shared/src/test/scala`; use
`modules/core/js`/`core/jvm` for platform boundaries and client/worker/server suites
for their adapters. Tests in a parent package can exercise subpackages, so absence
of a same-package spec is not a coverage measurement.

## Finding tests

Behavioral suites live beside their production packages in each module's
`src/test/scala/` tree. Shared tests cover domain transitions, validation,
serialization and supported execution on both platforms; adapter tests cover
platform-specific transport and lifecycle behavior.

Use `rg --files modules | rg '(Spec|Test)\.scala$'` for the current inventory.

## Run tests

```sh
npm ci
sbt 'coreJVM/test' 'coreJS/test' 'client/test' 'server/test' 'worker/test'
```

Node dependencies include jsdom for DOM fixtures. Browser checks additionally
require rebuilt client/worker artifacts and Playwright Chromium:

```sh
sbt buildClientDev buildWorkerDev
npx playwright install chromium
npm run test:static
npm run test:workbooks
```

The MySQL integration suite needs a disposable MySQL 8.4, for example in Docker
(the variables match the CI job):

```sh
docker run -d --name edu-mysql -p 3306:3306 -e MYSQL_DATABASE=edusquirrel_account_test \
  -e MYSQL_USER=account_test -e MYSQL_PASSWORD=disposable -e MYSQL_RANDOM_ROOT_PASSWORD=yes mysql:8.4
SQL_HOST=127.0.0.1 SQL_PORT=3306 SQL_DATABASE=edusquirrel_account_test SQL_USER=account_test \
  SQL_PW=disposable SERVER_SECRET=disposable sbt 'server / MysqlIntegration / test'
```

`ProgrammingUnitTestGraderSpec` (coreJVM) grades answers through the worker's
unit-test harness in a local `python3` via the test-only `CPythonRuntime` client; it
is skipped when no `python3` is installed.

For a narrow shared package:

```sh
sbt 'coreJVM/testOnly *RemoteHistoryCacheSpec *InteractionHistoryPackageSpec'
sbt 'coreJS/testOnly *RemoteHistoryCacheSpec *InteractionHistoryPackageSpec'
```

The [CI workflow](../.github/workflows/tests.yml) runs every Scala module, static
checks and browser checks. Domain tests should not require live backend accounts,
network timing or production databases. Use deterministic inputs, in-memory
services, injected schedulers and controlled futures. Restore DOM globals and
cancel owned subscriptions/timers so one suite cannot alter another's environment.

## Coverage and serialization are different checks

Only types crossing a [serialization boundary](serialization-boundaries.md) have
codecs, and the compiler rejects a boundary type without one. Round-trip tests
show that a codec encodes; behavioral tests must also check what a decoded model
can do, validation failures and protocol compatibility.

When measuring coverage, report the tool, platform, source scope and current
revision. Line/branch measurements, class inventories and test totals answer
different questions. Do not reuse historical percentages after code moves or API
removal, and do not add artificial assertions just to raise a count.

## Keep the suite focused

Keep checks for domain behavior, saved-data compatibility, persistence,
authentication and execution. Browser checks should exercise integration that
unit tests cannot prove; the runner uses two isolated processes and reports each
suite's duration. Use its filename filter for focused runs, for example
`npm run test:workbooks -- codemirror`.

Do not freeze chapter counts, lesson wording, button inventories, CSS dimensions
or source-code spelling in automated tests. Review those when editing content or
presentation. `WorkbookDefinitionsSpec` checks the current catalogue factories
for unique element IDs and serialization without fixing their lesson structure.

CI caches incremental Scala output separately from downloaded dependencies.
Build-definition changes start a new cache family but still restore the newest
older entry; Zinc recompiles what the change invalidates. Scala still checks the
current sources and test discovery on every run. A cold cache requires a full
compile; deleting fast domain assertions does little to reduce that cost. Most
of `core`'s compile time is uPickle codec derivation, paid once per platform.

The cross-built core compiles on two runners in parallel: `scala-jvm` tests
core JVM, server and desktop, and `scala-js` tests core JS, client and worker
and saves the Scala.js output. Each caches only its own `target/<project>`
directories. `browser-bundles` restores that cache and links the development
bundles in parallel, so `browser-tests` need not wait for the Scala tests.
`static-tests` runs the Node checks without a Scala build. When called by the
Pages workflow, `pages-build` restores the tested Scala.js output and
release-links in parallel with browser checks. Publication waits for the entire
reusable workflow. The required `tests` status gates every job, including
failures or cancellations in upstream jobs.

The MySQL job owns the server assembly/startup check. Browser bundles and Pages
artifacts use the producing build's attempt-specific name, so retries reuse the
correct upload. Run MySQL tests only against the disposable localhost database
specified in the [database test guide](database/interaction-history.md#verification).
