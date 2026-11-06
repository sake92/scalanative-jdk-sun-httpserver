# Planned improvements

Unchecked items are outstanding work, not implemented protections or confirmed vulnerabilities.
Current behavior and limits live in [COMPATIBILITY.md](COMPATIBILITY.md); measured external results live in [conformance/REPORT.md](conformance/REPORT.md).
Move completed behavior into the compatibility reference and validation evidence into the report; remove completed tasks here to avoid duplicate status lists.

## First: hardening policy and implementation

Agree on limits and configuration before implementation; preserve the JDK 8 public API and caller ownership of executors.

- [ ] Define connection admission, queued-work bounds and overload behavior.
- [ ] Decide whether upload-size limits belong in the server or application handlers.
- [ ] Turn each confirmed defect into a direct JUnit regression and validate the Native cross-build matrix.
- [ ] Use the [OWASP DoS guidance](https://cheatsheetseries.owasp.org/cheatsheets/Denial_of_Service_Cheat_Sheet.html) to review coverage and document remaining limits.
- [ ] Extend the bounded slow-client baseline to sustained load and legitimate slow/streaming clients when evaluating the agreed limits.

## Broader security coverage

- [ ] Evaluate Nmap `http-slowloris-check` as an independent diagnostic alongside measured exhaustion tests.
- [ ] Add reproducible Scala grammar/stateful fuzzing for malformed messages, framing boundaries and persistent-connection sequences.
- [ ] Test request-boundary disagreements through a real reverse proxy and this backend; keep deployment results separate from direct-server results.
- [ ] Expand RFC coverage beyond the current external suites without treating suite preferences as normative requirements.

## Conformance and portability

- [ ] Review the disputed empty-Host expectation with Http11Probe maintainers.
- [ ] Establish a repeatable external-suite CI baseline; distinguish local results from remote CI evidence.
- [ ] Investigate the recorded Scala Native `PosixThread.park` assertion during fixture TERM shutdown.
- [ ] Characterize reference-JDK early completion/closure on incomplete trailers; the bounded trailer baseline does not sustain worker saturation there.
- [ ] Validate release-mode builds and macOS/Windows behavior.
- [ ] Verify Java binary compatibility and direct protected-realm mutation on Scala 2.
- [ ] Resolve upstream placement and review each deliberate JDK behavior difference with its rationale and tests.

HTTPS, HTTP/2, provider discovery, upgrades/WebSockets, CONNECT tunnels and newer JDK APIs remain exclusions, not planned commitments.
