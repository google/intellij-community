---
name: Libraries Dashboard Spec
description: Requirements for the CLI tool that reports Maven library versions pinned in intellij.libraries.* wrapper modules and their upstream latest releases.
targets:
  - ../libraries-dashboard.mjs
---

# Libraries Dashboard Spec

Status: Draft
Date: 2026-04-17

## Summary
A single-file Bun CLI (`libraries-dashboard.mjs`) that scans every `intellij.libraries.*.iml` wrapper module in the repository, extracts the pinned Maven coordinates, and reports — per artifact — the current pinned version(s), the latest version available on Maven Central, an outdatedness classification, and a link to the artifact's GitHub releases page when derivable from the POM. The tool must run offline-friendly (disk cache) and produce both an HTML dashboard and an ANSI terminal table.

## Goals
- Discover every Maven library currently pinned through `intellij.libraries.*` wrapper modules without manual input.
- Surface which libraries are outdated and by how much (major / minor / patch).
- Provide a one-click path from a row in the dashboard to the upstream GitHub releases page, when the library is hosted on GitHub.
- Run with `bun` using only built-ins (`fs`, `path`, global `fetch`), no npm/package.json dependencies.
- Finish a cold run in under ~2 minutes on a typical connection; a warm (cached) run must complete in under 5 seconds.

## Non-goals
- Editing, bumping, or committing library version changes.
- Surfacing project-level libraries declared in `.idea/libraries/*.xml` (deferred to a future version).
- Surfacing `http_file` entries in `MODULE.bazel` or other Bazel-only Maven pins.
- Calling the GitHub API (which would require auth/tokens and rate-limit handling).
- Gating CI or builds — the tool must always exit `0` on successful report generation.

## Requirements

### Discovery
- The tool MUST recursively scan `community/libraries/` and `community/platform/libraries/` for files matching `intellij.libraries.*.iml`.
- The tool MUST NOT scan other directories or follow symlinks out of the repository.
- The tool MUST tolerate missing scan roots (e.g. partial checkout): an absent root MUST be skipped silently, not raised as an error.
  [@test] ../test/discovery.test.mjs

### Parsing
- For each discovered `.iml`, the tool MUST extract every `<library ... type="repository">…</library>` block.
- Within each block, the tool MUST read the `maven-id="groupId:artifactId:version"` attribute on the `<properties>` element as the canonical coordinate. When `maven-id` is absent, the block MUST be ignored.
- The parser MUST be tolerant of attribute order inside the `<library>` open tag (`name=` and `type=` may appear in either order).
- The parser MUST NOT use a full XML parser; it may rely on regular expressions given the stable iml serialization format.
  [@test] ../test/parsing.test.mjs

### Grouping
- Entries MUST be deduplicated by `groupId:artifactId`. Each group MUST carry:
  - the sorted list of distinct versions observed,
  - the list of referring modules (module name derived from the iml file name by stripping the `intellij.libraries.` prefix and `.iml` suffix),
  - the source file path per module reference.
- When a group contains more than one distinct version, the group's status MUST be `inconsistent` regardless of upstream comparison.

### Latest-version lookup
- The tool MUST query `https://repo1.maven.org/maven2/{groupPath}/{artifactId}/maven-metadata.xml` (not the Solr search endpoint, which returns unreliable `latestVersion` values for artifacts with legacy date-formatted tags).
- The latest version MUST be selected from the `<version>` entries by:
  - filtering to semver-shaped versions (`\d+\.\d+(\.\d+)?[+-]?` etc.),
  - excluding prereleases (alpha, beta, rc, cr, ea, milestone, snapshot, preview, dev, pr, m) unless the *current* pinned version is itself a prerelease,
  - picking the numerically highest remaining version.
- If no candidate survives filtering, the tool MUST fall back to the highest semver-shaped version including prereleases.
- Artifacts not available on Maven Central (HTTP ≠ 200, empty metadata) MUST be reported as `unknown` and MUST NOT crash the run.
  [@test] ../test/version-selection.test.mjs

### GitHub link derivation
- For each artifact, the tool SHOULD fetch the POM at `https://repo1.maven.org/maven2/{groupPath}/{artifactId}/{currentVersion}/{artifactId}-{currentVersion}.pom`.
- The tool SHOULD search the POM in this order:
  1. `<scm>` child `<url>`, `<connection>`, or `<developerConnection>` containing `github.com`.
  2. Top-level `<url>` pointing to `github.com`.
- The extracted URL MUST be normalized to `https://github.com/{owner}/{repo}` (strip `scm:git:` / `git+` / `git://` prefixes, trailing slashes, and `.git` suffix).
- The dashboard link MUST point to `https://github.com/{owner}/{repo}/releases/latest` — a plain browser URL that relies on GitHub's redirect to the current tag. The tool MUST NOT call the GitHub API.
- POM fetch failures MUST NOT abort the run; the artifact MUST simply report no GitHub link.
  [@test] ../test/github-link.test.mjs

### Classification
- Status values (in priority order for sorting worst-first): `major`, `minor`, `patch`, `inconsistent`, `unknown`, `ahead`, `up-to-date`.
- Classification MUST compare the highest observed current version against the resolved latest:
  - differing first numeric segment → `major`,
  - differing second segment → `minor`,
  - differing third or fourth segment → `patch`,
  - identical numeric parts → `up-to-date`,
  - current > latest → `ahead`,
  - unresolvable latest → `unknown`.

### Caching
- The tool MUST persist fetched `{latest, githubUrl, fetchedAt}` per `groupId:artifactId` to `<repo>/out/libraries-dashboard/cache.json` — the repo-root `/out/` directory is already git-ignored, keeping generated artifacts out of the source tree.
- The output directory MUST be created on demand (`mkdir -p`) before any write.
- Cache entries older than 24 hours MUST be ignored.
- `--no-cache` MUST skip reading the cache (writes still occur).
- `--refresh` MUST force a full refetch regardless of cache freshness.

### CLI surface
- Flags: `--no-cache`, `--refresh`, `--format=html|text|json|all`, `--open`, `-h`/`--help`.
- Default `--format` MUST be `all` (HTML file + terminal table).
- `--format=html` or `all` MUST write `<repo>/out/libraries-dashboard/dashboard.html`.
- `--format=json` MUST write `<repo>/out/libraries-dashboard/dashboard.json` and MUST NOT print the terminal table.
- `--format=text` MUST print the terminal table only and MUST NOT touch disk for output.
- `--open` MUST launch the system default browser on the generated HTML (macOS `open`, Windows `start`, Linux `xdg-open`) and MUST be a no-op when the selected format did not produce an HTML file.
- Unknown flags MUST exit with status `2` and a short error.
- Successful runs MUST exit with status `0`; fatal parse/IO errors MUST exit with status `1`.

### HTML output
- The generated HTML MUST be self-contained: no external scripts, no CDN references, no network dependencies at view time.
- It MUST support light and dark themes via `color-scheme: light dark` and system-color keywords (`Canvas`, `CanvasText`, `GrayText`).
- It MUST include, above the table, a live text filter over `groupId:artifactId`, a status dropdown, and status count chips.
- Column headers MUST be sortable (ascending/descending toggle) client-side.
- The modules column MUST render as a `<details>` element showing the count by default and expanding to the full module list with per-module version.
- Status MUST render as a colored badge (`.badge-major`, `.badge-minor`, `.badge-patch`, `.badge-inconsistent`, `.badge-up-to-date`, `.badge-ahead`, `.badge-unknown`).
- Each row MUST expose an **Action** column with a **Copy prompt** button for every artifact that is not `up-to-date` or `ahead`. Clicking the button MUST place a pre-filled, ready-to-paste prompt onto the system clipboard (via `navigator.clipboard.writeText`, falling back to a hidden `<textarea>` + `document.execCommand("copy")` when the async API is unavailable, e.g. when viewing the file over `file://` without clipboard permission).
- After a successful copy the button MUST flash a `.copied` state (~1.2s) and a transient toast ("Prompt copied to clipboard") MUST appear near the bottom of the viewport for ~1.6s.
- The prompt for outdated artifacts MUST include: the `groupId:artifactId`, the current (highest observed) pinned version, the target latest version, the repo-relative path of every iml file referencing the library with its pinned version, step-by-step guidance to update `maven-id`, the `<artifact>` / `CLASSES` / `SOURCES` URLs, and the `<sha256sum>`, and a reminder to run `./build/jpsModelToBazel.cmd`.
- The prompt for `unknown` artifacts MUST instead ask the agent to investigate the authoritative release source (since the artifact is absent from Maven Central) and propose a bump plan.
- All file paths embedded in prompts MUST be repo-relative (stripped of `REPO_ROOT` prefix), not absolute.

### Terminal output
- Output MUST be sorted worst-first (`major` → `minor` → `patch` → `inconsistent` → `unknown` → `ahead` → `up-to-date`); ties broken by `groupId:artifactId`.
- ANSI colors MUST be emitted only when `process.stdout.isTTY` is truthy and `NO_COLOR` is unset.
- A single summary line MUST follow the table: `Total: {n} ({status: count ...})`.

### Concurrency & networking
- Outbound HTTP requests (Maven metadata + POM) MUST be issued in a pool of at most 8 concurrent tasks.
- Every `fetch` MUST use `AbortSignal.timeout(15000)`.
- Every `fetch` MUST send a `user-agent: intellij-libraries-dashboard/<ver>` header.
- Timeouts and non-2xx responses MUST be handled as soft failures (the artifact is marked `unknown` / no GitHub link), never thrown up the call stack.

## User Experience
- The tool is a CLI only — no in-IDE integration is in scope.
- User-visible strings in the HTML and terminal output are English-only; localization is out of scope (the tool is for platform maintainers).
- Typical invocation: `bun community/build/libraries-dashboard/libraries-dashboard.mjs --open` from the repository root.

## Data & Backend
- Sources: Maven Central Repository 2 (`repo1.maven.org/maven2/...`). No authenticated endpoints, no JetBrains-internal repositories.
- Formats consumed: `maven-metadata.xml` (versioning `<version>` list), Maven POM (`<scm>`, `<url>`).
- Artifact coordinates are read exclusively from iml files; no JPS or Bazel model is loaded.
- Cache file shape: `{ "version": 1, "entries": { "<G:A>": { "latest": string|null, "githubUrl": string|null, "fetchedAt": number } } }`.

## Error Handling
- Missing scan roots: skipped silently.
- Unreadable `.iml` file: skipped; other files continue.
- `.iml` without any `maven-id`: skipped silently (many wrapper modules only re-export other modules).
- Artifact not on Maven Central: reported as `unknown`, GitHub link empty, counted in summary.
- POM lookup failure: GitHub column shows `—`, artifact is not otherwise degraded.
- Invalid JSON in cache: cache is discarded and rebuilt on the current run.
- No artifacts discovered: the tool MUST print a diagnostic and exit `1`.

## Testing / Local Run
- Cold run (forces full refetch): `bun community/build/libraries-dashboard/libraries-dashboard.mjs --refresh`.
- Warm run (uses cache): `bun community/build/libraries-dashboard/libraries-dashboard.mjs`.
- Verify HTML: `open out/libraries-dashboard/dashboard.html` (or pass `--open`).
- Verify JSON shape: `bun community/build/libraries-dashboard/libraries-dashboard.mjs --format=json && jq '.artifacts[0]' out/libraries-dashboard/dashboard.json`.
- Spot-check an artifact's reported latest against `https://central.sonatype.com/artifact/{groupId}/{artifactId}`.
- Unit tests (when authored) MUST live under `community/build/libraries-dashboard/test/*.test.mjs` and be runnable with `bun test community/build/libraries-dashboard/test`.

## Open Questions / Risks
- Inaccessible artifacts (`unknown`) are dominated by JetBrains-internal feeds (`ai.grazie.*`, `androidx.*`, `org.jetbrains.intellij.*`). A future version may add a second resolver against the JetBrains public Maven repository.
- Prerelease filter relies on a hard-coded keyword list; novel suffixes (e.g. `-nightly`, `-jb`) are not recognized as prereleases and may influence latest-version selection.
- `repo1.maven.org` does not rate-limit anonymously, but at >500 artifacts the sustained request volume may become impolite; the 8-way concurrency cap is a heuristic, not a negotiated budget.
- If Maven Central rolls out auth-required metadata endpoints, the fallback strategy is undefined.
