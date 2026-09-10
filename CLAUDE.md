# sonar-apple (payprotocol-appteam fork)

SonarQube plugin for Swift / Objective-C. Fork of [insideapp-fr/sonar-apple](https://github.com/insideapp-fr/sonar-apple)
0.5.1, maintained so it runs clean on **SonarQube Community Build 26.x** (the app team server).
Reader-facing overview and the diff against upstream: `README.md`. Contributor guide: `DEVELOP.md`.

Modules: `commons` (shared sensors/parsers/ANTLR plumbing) → `swift-lang`, `objc-lang` → `sonar-apple-plugin`
(packaging, Xcode + mobsfscan sensors).

## Build & test

JDK 17, Maven 3.8+.

```bash
mvn -B clean package     # compile + 227 tests + plugin jar
mvn -B license:check     # LGPL header check (CI runs it; `mvn license:format` fixes)
```

Artifact: `sonar-apple-plugin/target/sonar-apple-plugin-<version>.jar` (~4.4 MB).

**No compiler warning is acceptable.** `mvn -B clean package 2>&1 | grep -E '^\[WARNING\].*\.java'`
must print nothing. Deprecated API that cannot be dropped (see `Qualifiers` below) is silenced with
`@SuppressWarnings` plus a comment saying why, never left to warn.

## Verification: run it against a real server

Unit tests do not catch what SonarQube rejects at range-validation time, so any change to the ANTLR /
highlighting / CPD path must be verified against a throwaway server. **Never point this at the team
server on port 9000.**

```bash
docker rm -f sq-test; mkdir -p /tmp/sqtest/plugins
cp sonar-apple-plugin/target/sonar-apple-plugin-*.jar /tmp/sqtest/plugins/
docker run -d --name sq-test -p 9001:9000 \
  -v /tmp/sqtest/plugins:/opt/sonarqube/extensions/plugins \
  -e SONAR_TELEMETRY_ENABLE=false sonarqube:26.9.0.129388-community
until curl -s localhost:9001/api/system/status | grep -q '"status":"UP"'; do sleep 5; done
# admin/admin -> change password -> global analysis token -> create the project, then from the iOS repo:
sonar-scanner -Dsonar.host.url=http://localhost:9001 -Dsonar.token=... > /tmp/scan.log 2>&1
docker rm -f sq-test
```

Pass criteria:

- `grep -c "Unexpected error creating text range" /tmp/scan.log` → **0**
- `ANALYSIS SUCCESSFUL` in the log
- `/api/measures/component?component=<key>&metricKeys=ncloc,coverage,duplicated_lines_density,violations,files`
  returns non-zero `ncloc` and `files` (a broken language registration silently yields 0)
- `/api/sources/lines?key=<key>:<a file>.swift&from=1&to=5` shows `<span class="k">` markup
  (empty `code` spans mean highlighting was dropped)

Always send scanner output to a **file**. Piping it into `head` kills the scanner mid-analysis.

## Things that bite

- **Index units.** ANTLR (`CharStreams.fromStream`) indexes tokens by **code point**; SonarQube
  `TextRange` offsets are **UTF-16** code units. `SourceLine`/`SourceLinesProvider` keep the line table in
  code points and convert only the column (`SourceLine.toUtf16Column`). Do not "simplify" one of the two
  sides away — a single emoji in a file otherwise shifts every following token. The Swift grammar uses
  astral ranges (`Identifier_head` has `[\u{10000}-\u{1FFFD}]`), so switching ANTLR to a UTF-16 stream is
  not an option either.
- **Tokens that swallow their newline.** `Line_comment` is `'//' .*? ('\n' | EOF)`; a TextRange must not
  reach past the end of a line, so trailing `\n`/`\r` are trimmed before computing the end offset.
- **`Qualifiers` / `onQualifiers`** are deprecated for removal. The replacement (`ConfigScope` /
  `onConfigScopes`) needs plugin API ≥ 11.0.0.2664, which would break the 9.9 LTA support the manifest
  still advertises. Keep the old form until that support is dropped.
- **Test-only helpers are frozen.** `SensorContextTester` / `TestInputFileBuilder` / `DefaultInputFile`
  exist only up to `sonar-plugin-api-impl:25.6.0.109173`; newer releases (and every
  `sonar-plugin-api-test-fixtures`) no longer ship them. Do not "modernise" `sonar.version`.
- **Rule counts are asserted.** Regenerating a rules JSON breaks `*RulesDefinitionTest` and
  `*ProfileTest`, which assert exact sizes — update them in the same commit.

## Plugin API version policy

`sonar.api.version` = the API **bundled with the targeted server**, read from the image rather than
guessed (the upstream compatibility table lags):

```bash
cid=$(docker create sonarqube:26.9.0.129388-community)
docker cp $cid:/opt/sonarqube/lib/scanner/sonar-scanner-engine-community-*.jar /tmp/se.jar; docker rm $cid
unzip -p /tmp/se.jar sonar-api-version.txt     # -> 13.8.0.4399
```

`sonar.api.min.version` (the manifest's `Sonar-Version`, i.e. `pluginApiMinVersion`) is deliberately
*lower* — 9.14.0.375 — so the plugin still loads on old servers. That only holds while no API added after
9.14 is used; if you need newer API, raise this property and say so in the commit.

## Syncing with upstream

```bash
git remote add upstream https://github.com/insideapp-fr/sonar-apple.git   # once
git fetch upstream && git merge upstream/main
```

- Keep fork changes small and separable; every one of them is documented in the README table so a
  conflict can be resolved by reading it.
- `.github/workflows/ci.yml` is upstream's macOS + SonarCloud job, kept but triggered only manually
  (its secrets are not ours). Our gate is `.github/workflows/build.yml`. Keep it that way after a merge.
- Rule JSON updates are additive: keys retired by the linter stay in the file on purpose.
- Upstream fixes that supersede a local patch should be taken, and the README table row dropped in the
  same commit.
