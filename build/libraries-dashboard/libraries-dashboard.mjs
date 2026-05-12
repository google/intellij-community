#!/usr/bin/env bun

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Dashboard of Maven library versions pinned in intellij.libraries.* wrapper modules.
// Run: bun community/build/libraries-dashboard/libraries-dashboard.mjs [--no-cache|--refresh] [--format=html|text|json|all] [--open]

import {mkdir, readdir, readFile, writeFile} from "node:fs/promises"
import {existsSync} from "node:fs"
import {basename, dirname, join, resolve} from "node:path"
import {fileURLToPath} from "node:url"

const SELF_DIR = dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = resolve(SELF_DIR, "..", "..", "..")
const SCAN_ROOTS = [
  join(REPO_ROOT, "community/libraries"),
  join(REPO_ROOT, "community/platform/libraries"),
]
const OUT_DIR = join(REPO_ROOT, "out/libraries-dashboard")
const CACHE_PATH = join(OUT_DIR, "cache.json")
const HTML_OUT = join(OUT_DIR, "dashboard.html")
const JSON_OUT = join(OUT_DIR, "dashboard.json")
const CACHE_TTL_MS = 24 * 60 * 60 * 1000
const FETCH_CONCURRENCY = 8
const FETCH_TIMEOUT_MS = 15_000

function parseArgs(argv) {
  const opts = { cache: true, refresh: false, format: "all", open: false }
  for (const a of argv.slice(2)) {
    if (a === "--no-cache") opts.cache = false
    else if (a === "--refresh") opts.refresh = true
    else if (a === "--open") opts.open = true
    else if (a.startsWith("--format=")) opts.format = a.slice("--format=".length)
    else if (a === "-h" || a === "--help") {
      console.log(
        "Usage: bun libraries-dashboard.mjs [--no-cache] [--refresh] [--format=html|text|json|all] [--open]\n" +
          "  --no-cache  ignore cached Maven/POM responses\n" +
          "  --refresh   rewrite cache from scratch\n" +
          "  --format    output mode (default: all = html + terminal)\n" +
          "  --open      open the HTML report in the default browser"
      )
      process.exit(0)
    } else {
      console.error(`Unknown arg: ${a}`)
      process.exit(2)
    }
  }
  if (!["html", "text", "json", "all"].includes(opts.format)) {
    console.error(`Bad --format: ${opts.format}`)
    process.exit(2)
  }
  return opts
}

async function* walkIml(root) {
  if (!existsSync(root)) return
  const entries = await readdir(root, { withFileTypes: true })
  for (const e of entries) {
    const full = join(root, e.name)
    if (e.isDirectory()) {
      yield* walkIml(full)
    } else if (e.isFile() && e.name.startsWith("intellij.libraries.") && e.name.endsWith(".iml")) {
      yield full
    }
  }
}

// Extract every <library name="..." type="repository">...</library> block and its maven-id.
// Robust to attribute order: library block open tag may have name and type in either order.
const LIB_BLOCK_RE =
  /<library\b[^>]*?\btype="repository"[^>]*>([\s\S]*?)<\/library>/g
const LIB_NAME_RE = /\bname="([^"]+)"/
const MAVEN_ID_RE = /\bmaven-id="([^"]+)"/

function parseIml(content, filePath) {
  const out = []
  for (const m of content.matchAll(LIB_BLOCK_RE)) {
    const openTagEnd = content.indexOf(">", m.index)
    const openTag = content.slice(m.index, openTagEnd + 1)
    const nameMatch = openTag.match(LIB_NAME_RE)
    const body = m[1]
    const idMatch = body.match(MAVEN_ID_RE)
    if (!idMatch) continue
    const parts = idMatch[1].split(":")
    if (parts.length < 3) continue
    const [groupId, artifactId, version] = parts
    out.push({
      groupId,
      artifactId,
      version,
      libraryName: nameMatch ? nameMatch[1] : artifactId,
      filePath,
    })
  }
  return out
}

async function collectLibraries() {
  const all = []
  for (const root of SCAN_ROOTS) {
    for await (const imlPath of walkIml(root)) {
      let content
      try {
        content = await readFile(imlPath, "utf8")
      } catch {
        continue
      }
      for (const lib of parseIml(content, imlPath)) all.push(lib)
    }
  }
  return all
}

function moduleNameFromIml(imlPath) {
  return basename(imlPath).replace(/^intellij\.libraries\./, "").replace(/\.iml$/, "")
}

function groupByGA(entries) {
  const map = new Map()
  for (const e of entries) {
    const key = `${e.groupId}:${e.artifactId}`
    let group = map.get(key)
    if (!group) {
      group = {
        groupId: e.groupId,
        artifactId: e.artifactId,
        versions: new Set(),
        modules: [],
      }
      map.set(key, group)
    }
    group.versions.add(e.version)
    group.modules.push({
      module: moduleNameFromIml(e.filePath),
      version: e.version,
      libraryName: e.libraryName,
      path: e.filePath,
    })
  }
  return [...map.values()].map(g => ({
    ...g,
    versions: [...g.versions].sort(compareVersions),
    modules: g.modules.sort((a, b) => a.module.localeCompare(b.module)),
  }))
}

// --- Version parsing / comparison ---

function parseVersion(v) {
  if (!v) return null
  const m = v.match(/^(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:\.(\d+))?(.*)$/)
  if (!m) return null
  const [, a, b, c, d, suffix] = m
  return {
    parts: [a, b, c, d].map(x => (x == null ? 0 : Number(x))),
    hasSuffix: !!suffix && !/^(?:\.0+)*$/.test(suffix),
    suffix: suffix || "",
    raw: v,
  }
}

function compareVersions(a, b) {
  const pa = parseVersion(a)
  const pb = parseVersion(b)
  if (!pa || !pb) return String(a).localeCompare(String(b))
  for (let i = 0; i < 4; i++) {
    if (pa.parts[i] !== pb.parts[i]) return pa.parts[i] - pb.parts[i]
  }
  return pa.suffix.localeCompare(pb.suffix)
}

function classify(current, latest) {
  if (!latest) return "unknown"
  if (current === latest) return "up-to-date"
  const cv = parseVersion(current)
  const lv = parseVersion(latest)
  if (!cv || !lv) return current === latest ? "up-to-date" : "unknown"
  for (let i = 0; i < 4; i++) {
    if (cv.parts[i] > lv.parts[i]) return "ahead"
    if (cv.parts[i] < lv.parts[i]) {
      if (i === 0) return "major"
      if (i === 1) return "minor"
      return "patch"
    }
  }
  return cv.suffix === lv.suffix ? "up-to-date" : "patch"
}

// --- Cache ---

async function loadCache(useCache) {
  if (!useCache || !existsSync(CACHE_PATH)) return new Map()
  try {
    const raw = JSON.parse(await readFile(CACHE_PATH, "utf8"))
    const now = Date.now()
    const m = new Map()
    for (const [k, v] of Object.entries(raw.entries || {})) {
      if (v && typeof v.fetchedAt === "number" && now - v.fetchedAt < CACHE_TTL_MS) {
        m.set(k, v)
      }
    }
    return m
  } catch {
    return new Map()
  }
}

async function saveCache(map) {
  const entries = Object.fromEntries(map)
  await mkdir(OUT_DIR, { recursive: true })
  await writeFile(CACHE_PATH, JSON.stringify({ version: 1, entries }, null, 2))
}

// --- Network ---

async function fetchWithTimeout(url, init = {}) {
  return fetch(url, {
    ...init,
    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    headers: {
      "user-agent": "intellij-libraries-dashboard/1.0",
      ...(init.headers || {}),
    },
  })
}

const META_VERSION_RE = /<version>([^<]+)<\/version>/g
const PRE_RELEASE_RE =
  /(?:^|[-_.])(?:alpha|beta|rc|cr|ea|milestone|snapshot|preview|dev|pr|m)\d*(?=[-_.]|$)/i

function isLikelySemver(v) {
  return /^\d+\.\d+(?:\.\d+)?([+\-].+)?$/.test(v) || /^\d+$/.test(v)
}

function pickLatest(versions, current) {
  const currentIsPrerelease = PRE_RELEASE_RE.test(current || "")
  const candidates = versions.filter(v => {
    if (!isLikelySemver(v)) return false
    if (!currentIsPrerelease && PRE_RELEASE_RE.test(v)) return false
    return true
  })
  const pool = candidates.length > 0 ? candidates : versions.filter(isLikelySemver)
  if (pool.length === 0) return null
  pool.sort(compareVersions)
  return pool[pool.length - 1]
}

async function fetchLatestVersion(groupId, artifactId, currentVersion) {
  const groupPath = groupId.replaceAll(".", "/")
  const url = `https://repo1.maven.org/maven2/${groupPath}/${artifactId}/maven-metadata.xml`
  try {
    const r = await fetchWithTimeout(url)
    if (!r.ok) return null
    const xml = await r.text()
    const versions = []
    for (const m of xml.matchAll(META_VERSION_RE)) versions.push(m[1].trim())
    if (versions.length === 0) return null
    return pickLatest(versions, currentVersion)
  } catch {
    return null
  }
}

const POM_URL_RE = /<url>\s*(https:\/\/github\.com\/[^<\s]+?)\s*<\/url>/i
const POM_SCM_RE =
  /<scm>[\s\S]*?<(?:url|connection|developerConnection)>\s*([^<]*?github\.com[^<]+?)\s*<\/(?:url|connection|developerConnection)>[\s\S]*?<\/scm>/i

function normalizeGithubUrl(raw) {
  let u = raw.replace(/^scm:git:/, "").replace(/^git\+/, "").replace(/^git:\/\//, "https://")
  const m = u.match(/github\.com[/:]([^/\s]+)\/([^/\s]+?)(?:\.git)?(?:\/|$)/i)
  if (!m) return null
  return `https://github.com/${m[1]}/${m[2]}`
}

async function fetchGithubUrl(groupId, artifactId, version) {
  const groupPath = groupId.replaceAll(".", "/")
  const url = `https://repo1.maven.org/maven2/${groupPath}/${artifactId}/${version}/${artifactId}-${version}.pom`
  try {
    const r = await fetchWithTimeout(url)
    if (!r.ok) return null
    const pom = await r.text()
    const scm = pom.match(POM_SCM_RE)
    if (scm) {
      const n = normalizeGithubUrl(scm[1])
      if (n) return n
    }
    const u = pom.match(POM_URL_RE)
    if (u) {
      const n = normalizeGithubUrl(u[1])
      if (n) return n
    }
    return null
  } catch {
    return null
  }
}

async function runPool(items, workerFn, concurrency) {
  const queue = items.slice()
  const results = []
  async function worker() {
    while (queue.length) {
      const item = queue.shift()
      results.push(await workerFn(item))
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, worker))
  return results
}

async function enrich(groups, cache, opts) {
  const toFetch = []
  for (const g of groups) {
    const key = `${g.groupId}:${g.artifactId}`
    const cached = opts.refresh ? null : cache.get(key)
    if (cached && cached.latest !== undefined && cached.githubUrl !== undefined) {
      g.latest = cached.latest
      g.githubUrl = cached.githubUrl
    } else {
      toFetch.push(g)
    }
  }

  let done = 0
  const total = toFetch.length
  if (total > 0) {
    process.stderr.write(`Fetching metadata for ${total} artifacts...\n`)
  }

  await runPool(
    toFetch,
    async g => {
      const current = g.versions[g.versions.length - 1]
      const [latest, githubUrl] = await Promise.all([
        fetchLatestVersion(g.groupId, g.artifactId, current),
        fetchGithubUrl(g.groupId, g.artifactId, current),
      ])
      g.latest = latest
      g.githubUrl = githubUrl
      cache.set(`${g.groupId}:${g.artifactId}`, {
        latest,
        githubUrl,
        fetchedAt: Date.now(),
      })
      done++
      if (done % 10 === 0 || done === total) {
        process.stderr.write(`  ${done}/${total}\n`)
      }
    },
    FETCH_CONCURRENCY
  )

  for (const g of groups) {
    const current = g.versions[g.versions.length - 1]
    g.status = g.versions.length > 1 ? "inconsistent" : classify(current, g.latest)
    g.statusVsLatest = classify(current, g.latest)
  }
}

// --- Rendering ---

const STATUS_ORDER = [
  "major",
  "minor",
  "patch",
  "inconsistent",
  "unknown",
  "ahead",
  "up-to-date",
]

function statusRank(s) {
  const i = STATUS_ORDER.indexOf(s)
  return i === -1 ? STATUS_ORDER.length : i
}

function htmlEscape(s) {
  return String(s).replace(/[&<>"']/g, c => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  }[c]))
}

function buildUpdatePrompt(g, repoRoot) {
  const ga = `${g.groupId}:${g.artifactId}`
  const currentMax = g.versions[g.versions.length - 1]
  const groupPath = g.groupId.replaceAll(".", "/")
  const relFiles = g.modules.map(m => {
    const rel = m.path.startsWith(repoRoot + "/") ? m.path.slice(repoRoot.length + 1) : m.path
    return `  - ${rel} (currently @${m.version})`
  }).join("\n")
  if (!g.latest) {
    return [
      `Investigate the latest version of Maven library \`${ga}\`.`,
      ``,
      `It is pinned at ${currentMax} in the IntelliJ monorepo:`,
      relFiles,
      ``,
      `The artifact is not on Maven Central (https://repo1.maven.org/maven2/${groupPath}/${g.artifactId}/maven-metadata.xml). Identify the authoritative repository / release channel, determine the current stable version, and propose a version bump plan.`,
    ].join("\n")
  }
  return [
    `Update Maven library \`${ga}\` from ${currentMax} to ${g.latest} in the IntelliJ monorepo.`,
    ``,
    `Files to update:`,
    relFiles,
    ``,
    `For each file, inside the \`<library name="..." type="repository">\` block:`,
    `  1. Update \`maven-id="${ga}:<version>"\` on \`<properties>\` to the new version.`,
    `  2. Update the version segment in \`<artifact url="file://$MAVEN_REPOSITORY$/...">\`, in the CLASSES \`<root url="jar://...">\`, and in the SOURCES \`<root url="jar://...">\`.`,
    `  3. Refresh the \`<sha256sum>\` from https://repo1.maven.org/maven2/${groupPath}/${g.artifactId}/${g.latest}/${g.artifactId}-${g.latest}.jar.sha256`,
    ``,
    `Preserve the existing iml formatting (attribute order, indentation, no trailing newline changes).`,
    `After editing, run \`./build/jpsModelToBazel.cmd\` to regenerate BUILD.bazel files.`,
  ].join("\n")
}

function renderHtml(groups, generatedAt, repoRoot) {
  const rows = groups
    .slice()
    .sort((a, b) => {
      const r = statusRank(a.status) - statusRank(b.status)
      if (r !== 0) return r
      return `${a.groupId}:${a.artifactId}`.localeCompare(`${b.groupId}:${b.artifactId}`)
    })
    .map((g, i) => {
      const versions = g.versions.join(", ")
      const latest = g.latest ?? "—"
      const statusBadge = g.status
      const ghHref = g.githubUrl ? `${g.githubUrl}/releases/latest` : null
      const ghCell = ghHref
        ? `<a href="${htmlEscape(ghHref)}" target="_blank" rel="noopener">releases ↗</a>`
        : `<span class="muted">—</span>`
      const ga = `${g.groupId}:${g.artifactId}`
      const actionable = statusBadge !== "up-to-date" && statusBadge !== "ahead"
      const prompt = actionable ? buildUpdatePrompt(g, repoRoot) : ""
      const promptLabel = g.latest ? "Copy update prompt" : "Copy investigate prompt"
      const actionCell = actionable
        ? `<button class="copy-btn" type="button" data-prompt="${htmlEscape(prompt)}" title="${htmlEscape(promptLabel)}">📋 prompt</button>`
        : `<span class="muted">—</span>`
      return `
<tr data-status="${htmlEscape(statusBadge)}" data-ga="${htmlEscape(ga.toLowerCase())}">
  <td><span class="ga">${htmlEscape(ga)}</span></td>
  <td class="mono">${htmlEscape(versions)}</td>
  <td class="mono">${htmlEscape(latest)}</td>
  <td><span class="badge badge-${htmlEscape(statusBadge)}">${htmlEscape(statusBadge)}</span></td>
  <td>
    <details>
      <summary>${g.modules.length}</summary>
      <ul class="modules">${g.modules
        .map(
          m =>
            `<li><span class="mono">${htmlEscape(m.module)}</span> <span class="muted">@${htmlEscape(m.version)}</span></li>`
        )
        .join("")}</ul>
    </details>
  </td>
  <td>${ghCell}</td>
  <td>${actionCell}</td>
</tr>`
    })
    .join("")

  const counts = {}
  for (const g of groups) counts[g.status] = (counts[g.status] || 0) + 1
  const countsRow = STATUS_ORDER.filter(s => counts[s])
    .map(s => `<span class="badge badge-${s}">${s}: ${counts[s]}</span>`)
    .join(" ")

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>IntelliJ Libraries Dashboard</title>
<style>
  :root { color-scheme: light dark; }
  body { font: 14px/1.4 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 0; padding: 24px; background: Canvas; color: CanvasText; }
  h1 { font-size: 20px; margin: 0 0 4px; }
  .meta { color: GrayText; font-size: 12px; margin-bottom: 16px; }
  .controls { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }
  .controls input[type=search] { padding: 6px 10px; border: 1px solid #8886; border-radius: 6px; min-width: 240px; font: inherit; background: Canvas; color: inherit; }
  .controls select { padding: 6px 10px; border: 1px solid #8886; border-radius: 6px; font: inherit; background: Canvas; color: inherit; }
  .counts { display: flex; gap: 6px; flex-wrap: wrap; }
  table { border-collapse: collapse; width: 100%; }
  th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #8884; vertical-align: top; }
  th { position: sticky; top: 0; background: Canvas; cursor: pointer; user-select: none; }
  th:hover { background: #8881; }
  .mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12.5px; }
  .ga { font-weight: 600; }
  .muted { color: GrayText; }
  .modules { margin: 6px 0 0; padding-left: 18px; }
  .modules li { margin: 1px 0; }
  .badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 600; letter-spacing: 0.3px; }
  .badge-major { background: #e5484d22; color: #e5484d; }
  .badge-minor { background: #f5a62322; color: #b06d00; }
  .badge-patch { background: #3e63dd22; color: #3e63dd; }
  .badge-inconsistent { background: #8b5cf622; color: #8b5cf6; }
  .badge-up-to-date { background: #30a46c22; color: #30a46c; }
  .badge-ahead { background: #8888; color: inherit; }
  .badge-unknown { background: #8883; color: GrayText; }
  a { color: #3e63dd; text-decoration: none; }
  a:hover { text-decoration: underline; }
  .copy-btn { font: inherit; padding: 4px 10px; border: 1px solid #8886; border-radius: 6px; background: Canvas; color: inherit; cursor: pointer; white-space: nowrap; }
  .copy-btn:hover { background: #8881; }
  .copy-btn.copied { background: #30a46c22; border-color: #30a46c66; color: #30a46c; }
  .copy-btn:disabled { opacity: 0.4; cursor: not-allowed; }
  #toast { position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%); padding: 8px 14px; border-radius: 8px; background: CanvasText; color: Canvas; font-size: 13px; opacity: 0; transition: opacity 0.2s; pointer-events: none; }
  #toast.show { opacity: 0.9; }
</style>
</head>
<body>
<h1>IntelliJ Libraries Dashboard</h1>
<div class="meta">${groups.length} artifacts · generated ${htmlEscape(generatedAt)}</div>
<div class="controls">
  <input id="q" type="search" placeholder="filter by groupId:artifactId…" autofocus>
  <select id="status">
    <option value="">all statuses</option>
    ${STATUS_ORDER.map(s => `<option value="${s}">${s}</option>`).join("")}
  </select>
  <div class="counts">${countsRow}</div>
</div>
<table>
  <thead>
    <tr>
      <th data-sort="ga">Artifact</th>
      <th data-sort="versions">Current</th>
      <th data-sort="latest">Latest</th>
      <th data-sort="status">Status</th>
      <th data-sort="modules"># modules</th>
      <th>GitHub</th>
      <th>Action</th>
    </tr>
  </thead>
  <tbody>${rows}</tbody>
</table>
<div id="toast" role="status" aria-live="polite"></div>
<script>
const q = document.getElementById("q")
const statusSel = document.getElementById("status")
const tbody = document.querySelector("tbody")
function apply() {
  const needle = q.value.trim().toLowerCase()
  const s = statusSel.value
  for (const tr of tbody.rows) {
    const ga = tr.dataset.ga
    const st = tr.dataset.status
    const show = (!needle || ga.includes(needle)) && (!s || st === s)
    tr.style.display = show ? "" : "none"
  }
}
q.addEventListener("input", apply)
statusSel.addEventListener("change", apply)
document.querySelectorAll("th[data-sort]").forEach((th, colIdx) => {
  let asc = true
  th.addEventListener("click", () => {
    asc = !asc
    const rows = [...tbody.rows]
    rows.sort((a, b) => {
      const av = a.cells[colIdx].innerText
      const bv = b.cells[colIdx].innerText
      const na = Number(av), nb = Number(bv)
      if (!Number.isNaN(na) && !Number.isNaN(nb)) return asc ? na - nb : nb - na
      return asc ? av.localeCompare(bv) : bv.localeCompare(av)
    })
    for (const r of rows) tbody.appendChild(r)
  })
})
const toast = document.getElementById("toast")
let toastTimer
function showToast(msg) {
  toast.textContent = msg
  toast.classList.add("show")
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => toast.classList.remove("show"), 1600)
}
async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    const ta = document.createElement("textarea")
    ta.value = text
    ta.style.position = "fixed"
    ta.style.opacity = "0"
    document.body.appendChild(ta)
    ta.select()
    let ok = false
    try { ok = document.execCommand("copy") } catch {}
    ta.remove()
    return ok
  }
}
tbody.addEventListener("click", async (ev) => {
  const btn = ev.target.closest(".copy-btn")
  if (!btn) return
  const ok = await copyText(btn.dataset.prompt)
  if (ok) {
    btn.classList.add("copied")
    setTimeout(() => btn.classList.remove("copied"), 1200)
    showToast("Prompt copied to clipboard")
  } else {
    showToast("Copy failed — clipboard unavailable")
  }
})
</script>
</body>
</html>
`
}

// --- Terminal rendering ---

const IS_TTY = process.stdout.isTTY && !process.env.NO_COLOR
const C = IS_TTY
  ? {
      reset: "\x1b[0m",
      bold: "\x1b[1m",
      dim: "\x1b[2m",
      red: "\x1b[31m",
      green: "\x1b[32m",
      yellow: "\x1b[33m",
      blue: "\x1b[34m",
      magenta: "\x1b[35m",
      gray: "\x1b[90m",
    }
  : Object.fromEntries(["reset", "bold", "dim", "red", "green", "yellow", "blue", "magenta", "gray"].map(k => [k, ""]))

const STATUS_COLOR = {
  major: C.red,
  minor: C.yellow,
  patch: C.blue,
  inconsistent: C.magenta,
  "up-to-date": C.green,
  ahead: C.gray,
  unknown: C.gray,
}

function renderTerminal(groups) {
  const sorted = groups
    .slice()
    .sort((a, b) => {
      const r = statusRank(a.status) - statusRank(b.status)
      if (r !== 0) return r
      return `${a.groupId}:${a.artifactId}`.localeCompare(`${b.groupId}:${b.artifactId}`)
    })
  const cols = [
    { title: "Artifact", get: g => `${g.groupId}:${g.artifactId}` },
    { title: "Current", get: g => g.versions.join(", ") },
    { title: "Latest", get: g => g.latest ?? "—" },
    { title: "Status", get: g => g.status, color: g => STATUS_COLOR[g.status] || "" },
    { title: "#", get: g => String(g.modules.length), align: "right" },
    { title: "GitHub", get: g => (g.githubUrl ? g.githubUrl.replace("https://github.com/", "") : "—") },
  ]
  const widths = cols.map(c => c.title.length)
  for (const g of sorted) {
    cols.forEach((c, i) => {
      widths[i] = Math.max(widths[i], c.get(g).length)
    })
  }
  const pad = (s, w, align) => (align === "right" ? s.padStart(w) : s.padEnd(w))
  const header = cols.map((c, i) => pad(c.title, widths[i], c.align)).join("  ")
  const sep = cols.map((_, i) => "─".repeat(widths[i])).join("  ")
  const lines = [C.bold + header + C.reset, C.dim + sep + C.reset]
  for (const g of sorted) {
    const line = cols
      .map((c, i) => {
        const cell = pad(c.get(g), widths[i], c.align)
        return c.color ? c.color(g) + cell + C.reset : cell
      })
      .join("  ")
    lines.push(line)
  }
  lines.push("")
  const counts = {}
  for (const g of groups) counts[g.status] = (counts[g.status] || 0) + 1
  const summary = STATUS_ORDER.filter(s => counts[s])
    .map(s => `${STATUS_COLOR[s] || ""}${s}: ${counts[s]}${C.reset}`)
    .join("  ")
  lines.push(`${C.bold}Total:${C.reset} ${groups.length}  (${summary})`)
  return lines.join("\n")
}

// --- Main ---

async function main() {
  const opts = parseArgs(process.argv)

  const entries = await collectLibraries()
  if (entries.length === 0) {
    console.error("No intellij.libraries.*.iml files with maven-id found.")
    process.exit(1)
  }
  const groups = groupByGA(entries)

  const cache = await loadCache(opts.cache && !opts.refresh)
  await enrich(groups, cache, opts)
  await saveCache(cache)

  const generatedAt = new Date().toISOString()

  if (opts.format === "text" || opts.format === "all") {
    console.log(renderTerminal(groups))
  }
  if (opts.format === "html" || opts.format === "all") {
    await mkdir(OUT_DIR, { recursive: true })
    await writeFile(HTML_OUT, renderHtml(groups, generatedAt, REPO_ROOT))
    console.error(`\nHTML: ${HTML_OUT}`)
  }
  if (opts.format === "json") {
    const plain = {
      generatedAt,
      artifacts: groups.map(g => ({
        groupId: g.groupId,
        artifactId: g.artifactId,
        versions: g.versions,
        latest: g.latest,
        status: g.status,
        githubUrl: g.githubUrl,
        githubReleasesUrl: g.githubUrl ? `${g.githubUrl}/releases/latest` : null,
        modules: g.modules,
      })),
    }
    await mkdir(OUT_DIR, { recursive: true })
    await writeFile(JSON_OUT, JSON.stringify(plain, null, 2))
    console.error(`JSON: ${JSON_OUT}`)
  }

  if (opts.open && (opts.format === "html" || opts.format === "all")) {
    const { spawn } = await import("node:child_process")
    const cmd = process.platform === "darwin" ? "open" : process.platform === "win32" ? "start" : "xdg-open"
    spawn(cmd, [HTML_OUT], { detached: true, stdio: "ignore" }).unref()
  }
}

main().catch(err => {
  console.error(err)
  process.exit(1)
})
