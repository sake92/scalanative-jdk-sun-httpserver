# External HTTP/1.1 checks

These probes run against a standalone application using the real server and TCP
sockets. The same application can run on Native or against the host JDK's HTTP
server. [The initial report](REPORT.md) records results and qualifications.

## Prepare the tools

Requires Linux with `setsid` and `kill`, Scala CLI, JDK 17+, Git, the existing Scala Native build toolchain,
Deno 2.5.6 for h1spec, and .NET SDK 10.0.100 for Http11Probe. These are external
test tools, not library dependencies. Deno can be installed locally with npm:

```sh
mkdir -p target/conformance-tools
npm install --prefix target/conformance-tools deno@2.5.6

git clone https://github.com/uNetworking/h1spec.git target/conformance-tools/h1spec
git -C target/conformance-tools/h1spec checkout f0a5650a20c575fbea0f7179a3a9cfa50f20ba6e

git clone https://github.com/MDA2AV/Http11Probe.git target/conformance-tools/Http11Probe
git -C target/conformance-tools/Http11Probe checkout d50d818a0e008e9feb3bb9860ad5d9cc40342bc3
dotnet build target/conformance-tools/Http11Probe/src/Http11Probe.Cli -c Release
```

The runner requires those exact suite revisions and rejects tracked modifications.
The external sources stay in ignored build directories; none are vendored here.
[h1spec](https://github.com/uNetworking/h1spec) has no license file at its pinned
revision. [Http11Probe](https://github.com/MDA2AV/Http11Probe) is MIT licensed.

## Run against Native

From the repository root:

```sh
sbt conformance/nativeLink

scala run conformance/run.scala --server=false -- \
  --suite h1spec --checkout target/conformance-tools/h1spec \
  --deno target/conformance-tools/node_modules/.bin/deno \
  --server target/out/native0.5/scala-3.9.0/httpserver-conformance/http-conformance-server \
  --output target/conformance-results/native-h1spec

scala run conformance/run.scala --server=false -- \
  --suite http11probe --checkout target/conformance-tools/Http11Probe \
  --probe-dll target/conformance-tools/Http11Probe/src/Http11Probe.Cli/bin/Release/net10.0/Http11Probe.Cli.dll \
  --server target/out/native0.5/scala-3.9.0/httpserver-conformance/http-conformance-server \
  --output target/conformance-results/native-http11probe
```

Each output directory must be new. Choose another name for subsequent runs.
`--deno` and `--dotnet` accept executable paths; otherwise tools are found on PATH.
The Native executable path above is for the default Scala 3.9.0 build.

## Compare with the JDK server

Compile the shared application in the isolated JVM build using Scala 2.13, so
the same class files can run on JDK 8 and newer:

```sh
(cd jvm-reference && sbt ';++2.13.18;conformanceClasspath')

scala run conformance/run.scala --server=false -- \
  --suite h1spec --checkout target/conformance-tools/h1spec \
  --deno target/conformance-tools/node_modules/.bin/deno \
  --server "java -cp $(cat jvm-reference/target/conformance-classpath.txt) conformance.ConformanceServer" \
  --output target/conformance-results/jdk-h1spec
```

Replace `java` with the intended JDK's absolute executable path. Use the same
server command with the Http11Probe invocation above for its reference run.
`--server` is split into arguments without a shell; use single or double quotes
around paths containing spaces inside that command. Backslashes are literal,
not shell escapes. The JVM build does not depend on the Native implementation.

The Scala runner pins `mainargs` for CLI parsing, `ujson` for report handling,
and Apache Ant's `Commandline` utility for quoted command splitting. These are
script dependencies; they are not added to the HTTP library's build.

## What a run does

`ConformanceServer` binds to an OS-assigned loopback port and uses a caller-owned
64-thread pool. The runner selects its application profile through
`HTTP_CONFORMANCE_PROFILE`:

- h1spec: echo request bodies for every method and path.
- Http11Probe: echo POST bodies, expose request headers at `/echo`, and return
  `OK` for other requests. HEAD responses are bodyless in both profiles.

The server prints its port after `start()`. The runner allows 30 seconds for
readiness and 900 seconds per probe invocation, and saves server output, probe
output and command/revision metadata. It terminates owned process groups and
reaps direct children on completion, failure, timeout or interruption. The
application's shutdown hook stops the server and its worker pool.
An externally interrupted run can leave partial reports; its logs are diagnostic
evidence rather than a completed suite result.

Http11Probe runs the Compliance, Smuggling, MalformedInput, Normalization and
Capabilities categories. Cookies and WebSockets are excluded: the application
does not implement a cookie endpoint or WebSocket upgrades. Use repeatable
`--category` options to select fewer categories. Capability checks for caching,
compression and conditional responses can describe missing application features;
they do not automatically identify transport defects.

The runner returns nonzero for failed probes or process failures. Http11Probe's
own CLI returns zero even for failed probes at this revision, so the runner also
checks its JSON verdicts, including failures in unscored tests. Warnings remain
visible and do not fail the run. Original reports are preserved without changing
expectations or suppressing known failures.

## Interpreting results

h1spec launches its 33 cases concurrently, uses a 500 ms read timeout, and checks
only one socket read per response. Body assertions can therefore fail when a
valid response arrives in several reads. Confirm such failures with a complete
HTTP response reader before attributing them to the server.

Http11Probe distinguishes scored and unscored cases, failures and warnings.
Its JSON `total` includes cases skipped by the category filter; the number
actually executed is `total - skipped`. Do not sum `total` across category runs.

The RFCs decide protocol correctness; JDK comparisons explain compatibility
differences. A suite score is evidence about its particular cases, not proof of
complete conformance. No load or performance claims follow from these runs.
