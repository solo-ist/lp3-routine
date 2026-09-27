# Routine security audit

Audit date: 2026-09-27 UTC (2026-09-26 America/New_York).
Reviewed revision: `0e53ee8e85f66b1c17f8d14b02d28bb7ebda90c0`, version 0.4.1.
Scope: Routine only, as requested. No application, build, or release code was changed.

The review identified two medium-priority findings and one low-priority
validation defect. No critical issue or direct remote attack path was found
in the reviewed application code. This is a source audit with a reproduced
release-parser defect, not a completed device penetration test or a clean
bill of health for the release artifact.

| ID | Project priority | Finding | Evidence |
| --- | --- | --- | --- |
| R-01 | Medium | Vulnerable Gradle version; no dependency verification | Version/configuration inspection and maintainer advisories |
| R-02 | Medium | Backup release check accepts unrelated manifest strings | Actual AWK parser reproduced against synthetic aapt2 output |
| R-03 | Low | Import duration limits are checked after narrowing | Deterministic source-level arithmetic; JVM/device test blocked |

## R-01: update the build tool and verify dependencies

Locations: `gradle/wrapper/gradle-wrapper.properties:4`,
`settings.gradle.kts:1`, `scripts/release.sh:32`.

The wrapper pins Gradle 9.0.0. This falls within the affected range of
[CVE-2026-22816 / GHSA-w78c-w6vf-rw82](https://github.com/gradle/gradle/security/advisories/GHSA-w78c-w6vf-rw82)
and [CVE-2026-22865 / GHSA-mqwm-5m85-gmcv](https://github.com/gradle/gradle/security/advisories/GHSA-mqwm-5m85-gmcv).
Both upstream advisories are rated high. They concern fallback to other
repositories after certain resolution failures; the patched versions are
8.14.4 and 9.3.0 or later.

This project declares multiple repositories without strict content filtering
and has no `gradle/verification-metadata.xml`. The distribution ZIP checksum
is pinned, which protects that download but does not authenticate Maven
artifacts. A compromised build plugin executes on the build host; release
builds also receive the signing password through their environment.

The practical attack needs repository/network failure conditions and an
attacker-controlled artifact source. The declared repositories are established
public services; there is no demonstrated attacker-controlled repository or
evidence of compromise here. Medium project priority reflects those
preconditions, while preserving the upstream high severity.

Remediation: select a patched Gradle version compatible with the Android
plugin/JDK, refresh the wrapper and its official checksum, and run release
lint and tests. Add dependency verification from independently reviewed
checksums and constrain repository content. Do not blindly trust generated
verification metadata from a potentially compromised first resolution.

## R-02: the release gate does not identify the actual backup attribute

Location: `scripts/verify-release.sh:74–85`.

The AWK predicate matches `/android:allowBackup/` anywhere in a line, then
looks for `=false` anywhere in that line. It also continues into several
child element types instead of restricting itself to direct application
attributes. An unrelated label or child metadata name containing
`android:allowBackup=false` is consequently accepted when the actual
application attribute is missing.

Reproduce from the repository root:

```sh
python3 docs/audit/reproduce_backup_parser.py
```

Observed results on the audited revision:

| Fixture | Expected | Actual |
| --- | --- | --- |
| Application explicitly disables backup | Accept | Accept |
| Application explicitly enables backup | Reject | Reject |
| Backup attribute absent | Reject | Reject |
| Attribute absent, spoofing text in application label | Reject | Accept |
| Attribute absent, spoofing text in child metadata | Reject | Accept |

The reproducer extracts and executes the production AWK program; it does
not substitute a reimplementation. Exit status 1 indicates the reproduced
weakness. These are synthetic aapt2 text fixtures: no malicious APK was
built, signed, installed, or demonstrated to back up data.

Impact: the gate's promise to require an explicit application-level
`allowBackup=false` can fail. This requires an artifact that still passes
the independent signer check; it is not a signing bypass. The current
source and existing merged release manifest explicitly disable backup and
reference exclusion rules, so this finding does not establish a present
backup leak.

Remediation: parse the application attribute structurally, match the exact
attribute name/resource ID and boolean value, and stop at the first child
element when using aapt2 text. Reject missing, malformed, or ambiguous
values. Add the two negative fixtures to release-gate regression coverage.
Also verify the referenced cloud/device-transfer exclusion resource;
the current gate never inspects it.

## R-03: oversized and negative durations can become short valid timers

Location: `app/src/main/java/ist/solo/routine/ImportParser.java:96–103`.

For minutes, the parser multiplies by 60, rounds to a long, then casts to
an int before validating the 0–10,800-second range. Java's narrowing
conversion loses high bits. For example:

```json
{"v":1,"routines":[{"name":"overflow","steps":[{"name":"step","min":71582789}]}]}
```

`71582789 * 60 = 4294967340`, which narrows to **44 seconds** and passes
the range check. Similarly, `min: -71582788` becomes **16 seconds**.
These outcomes follow directly from the conversion; the Java parser was
not executed during this audit because the runtime was unavailable.

The `getInt` / `optInt` calls for seconds, version, and threshold also merit
strict type/range tests rather than relying on JSON coercion. Existing
import tests cover an ordinary out-of-range duration, not integer wrapping
or non-finite inputs.

Impact is limited: this is a local file import, the user must arrange the
file transfer, and a preview precedes saving. The defect can silently
change imported plans; it is not evidence of arbitrary code execution or
cross-app access.

Remediation: validate the original value for numeric type, finiteness,
and allowed range before rounding or narrowing. Check integer fields
without lossy coercion. Cover the above examples and numeric boundaries
in tests, including tests against Android's actual JSON implementation.

## Additional hardening and unresolved checks

- **Import resource exhaustion:** `ImportParser.java:55` constructs an
  entire JSON tree before validating its shape. The
  [AOSP JSON tokenizer](https://android.googlesource.com/platform/libcore/+/master/json/src/main/java/org/json/JSONTokener.java)
  recursively parses containers. A deeply nested unknown field can fit
  within 64 KB. Inference: stack exhaustion could escape the
  `JSONException` catch and crash on each home-screen launch, before the
  discard UI appears. This needs confirmation on the target LightOS/Android
  build; it is not counted as a reproduced finding. Enforce a nesting limit
  and ensure bad files can be discarded without reparsing them.
- **File read bound:** `ImportFile.java:39–41` checks length and then calls
  `Files.readAllBytes`. Concurrent file growth can exceed the initial limit.
  Use a bounded read that stops at MAX_BYTES + 1. The writer currently needs
  app-directory access (normally adb or privileged access), which limits
  the security impact.
- **Privacy wording:** README's “nothing ... ever leaves the phone” is
  stronger than absence of INTERNET establishes. `StepNotice.java:47–49`
  deliberately shares routine/step names with Android's notification
  system. Device notification/accessibility/keyboard policies remain trust
  boundaries. No unauthorized export through those paths was demonstrated.
- **Artifact provenance:** the release script checks a clean tree and
  verifies an APK path, then installs that mutable path. It does not freeze
  source inputs during the build or the artifact after verification. An
  isolated release directory and immutable verified copy would strengthen
  its “exactly the artifact” guarantee. No concurrent replacement attack
  was performed.

## Protections observed

- No runtime third-party dependencies are declared; JUnit 4.13.2 and
  org.json 20240303 are test-only. No INTERNET permission or application
  networking, WebView, shell execution, or dynamic code loading was found.
- Player, editors, summary, and step-alarm receiver are non-exported.
  HomeActivity does not consume caller-controlled extras. BootReceiver
  checks the boot action; SdkMarkerReceiver is a no-op.
- Alarm and notification pending intents use explicit components and
  `FLAG_IMMUTABLE`.
- Database lookups bind values; writes use ContentValues. Preferences
  are private, and SQLite uses app-private storage. No hard-coded
  credentials were observed in reviewed source/build scripts.
- Backup is explicitly disabled; cloud and device-transfer rules exclude
  root, files, databases, preferences, and external files.
- Imports use a fixed app-specific filename, impose size/count/name limits,
  and require confirmation before database writes. Stored run history
  copies routine definitions rather than retaining editable references.
- Debug builds have a separate application ID and signing identity; debug
  receiver code is separated from the release source set.
- The release script fails on verification failure. The verifier requires
  a pre-enrolled signer pin and checks for a debuggable APK.

## Validation and limits

Completed: source/build/manifest/release-script review; maintainer advisory
lookup; five backup-parser fixtures (three correct results, two reproduced
failures); `bash -n` for both release scripts; existing APK ZIP integrity
check. The APK contains one DEX and no native libraries. ZIP integrity is
not cryptographic signature verification.

Existing release APK SHA-256:
`107ab3a7116c5c61a4695daf6d6135ad001584eb805a7a08ef093236184445fb`.
This artifact was not rebuilt, and correspondence to HEAD was not proven.

Existing debug-test reports contain 37 tests across six suites, with zero
failures/errors/skips. Those are historical results, not a fresh audit run.

Fresh release lint/unit tests could not start: Java was unavailable to the
session. APK signer verification could not start: Android SDK access was
denied. Retrying with escalation did not resolve either constraint. The
initial full verifier mock harness also hit filesystem restrictions; only
its extracted backup parser was successfully exercised. Official wrapper
JAR checksum retrieval was unavailable, so the wrapper binary's origin was
not independently verified.

Sibling audits were not read; the user chose Routine-only scope after
access failed. The installed code-review skill was also inaccessible.
Denied environment/configuration/provisioning files, private signing keys,
and comprehensive Git-history secret scanning were outside the review.
No device, emulator, signing credentials, installation, or external
publication was used.

After remediation, rerun release lint/unit tests, verify a freshly built
APK with the real SDK tools, and test malformed imports on a debug device.
The JSON Maven dependency used by local unit tests is not identical to the
Android platform implementation, so it cannot alone establish on-device
parsing behavior.

## Remediation (2026-09-27)

All three findings and all four hardening notes were addressed in version 0.4.2.

| ID | Fix | Verified by |
| --- | --- | --- |
| R-01 | Gradle 9.0.0 → **9.5.1** (≥ 9.3.0 is patched; 9.6+ drops an internal API that AGP 8.x needs). Distribution and wrapper-JAR SHA-256 pinned and checked against `services.gradle.org`. Every dependency group now resolves from exactly one repository (`settings.gradle.kts`), and `FAIL_ON_PROJECT_REPOS` is set. Added `gradle/verification-metadata.xml` (SHA-256, 472 artifacts). | `scripts/crosscheck-verification.py` re-downloaded every artifact from its canonical repository, bypassing Gradle's cache: 472/472 match. A deliberately corrupted hash failed the build. |
| R-02 | The awk parser is replaced by `scripts/manifest_gate.py`, which parses the xmltree and reads only a direct `<application>` attribute, matched by exact name, resource ID and value. Missing, repeated or unrecognised values fail. The gate now also resolves `dataExtractionRules` (release builds shorten its path) and requires every domain excluded in both `cloud-backup` and `device-transfer`, with no `include`. | `scripts/test_manifest_gate.py`, 20 cases including both audit fixtures, runs inside the gate before every release. The real installed APK passes. |
| R-03 | Numbers are type-checked (must be JSON numbers, finite, and whole where required) and range-checked **before** any rounding or narrowing, for `min`, `sec`, `threshold` and `v`. | Unit tests cover the audit's `71582789` and `-71582788` examples, `sec` overflow, `1e400`, strings, fractions and inclusive boundaries. On-device, Android's own JSON implementation rejected them too. |
| Nesting | Depth is counted without parsing and capped at 8 before the tokenizer sees the text. Any parser `RuntimeException` or `StackOverflowError` becomes a rejection, so a bad file always reaches the discard UI. | 5,000-deep nesting: rejected on device with "nested more than 8 levels deep"; no crash. |
| Read bound | `ImportFile` reads at most `MAX_BYTES + 1` bytes, instead of trusting a length checked before the read. | A 70 KB file was rejected on device. |
| Privacy wording | The README and manifest now say Routine can't send anything off the phone itself. They also state plainly that step names reach the notification system and its listeners. | — |
| Provenance | `release.sh` builds a `git archive` export of HEAD in a private temp directory, copies the APK read-only, verifies that copy with the same revision's gate, and installs exactly that file. | Used for the 0.4.2 release. |

The reproducer in `docs/audit/` targeted the old awk and no longer applies. It stays out of the repository; its fixtures live on in `scripts/test_manifest_gate.py`.
