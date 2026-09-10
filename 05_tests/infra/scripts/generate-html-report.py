#!/usr/bin/env python3
"""Generate a self-contained HTML test report.

The report answers one question for a reader who has never seen this project:
*how is this software tested, and how much of it is actually covered?*

Everything it states is derived from machine-readable evidence:

  * JUnit XML     what ran, what passed, how long it took
  * Kotlin source how many tests are *declared* -- which is how a class that
                  never runs is caught, because Gradle reports an @Ignore'd
                  class as a single skipped entry no matter how many tests it
                  contains
  * coverage-map.tsv  which test claims which requirement
  * the SRS       the full list of requirements, i.e. the denominator
  * the L4 checklists  the manual tests only a human can discharge

No prose is parsed anywhere. Every input is either XML, a tab-separated file,
or a rigidly formatted table, so the report cannot quietly mis-read a document
and overstate coverage -- which is the failure mode it exists to prevent.

Output is deterministic: identical inputs produce a byte-identical file, apart
from the generation timestamp, which can be pinned with --source-date.

Usage:
    generate-html-report.py [--output FILE] [--source-date ISO8601]
                            [--title TEXT] [--l1-results DIR] [--l2-results DIR]
"""

from __future__ import annotations

import argparse
import base64
import html
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import OrderedDict
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[3]

DEFAULT_L1_RESULTS = PROJECT_ROOT / "app/build/test-results/testDebugUnitTest"
DEFAULT_L2_RESULTS = PROJECT_ROOT / "app/build/outputs/androidTest-results/connected/debug"
COVERAGE_MAP = PROJECT_ROOT / "05_tests/coverage-map.tsv"
SCOPE_MAP = PROJECT_ROOT / "05_tests/scope-map.tsv"
SRS = PROJECT_ROOT / "01_requirements/DrivingCoach_SRS_v1.md"
L4_DIR = PROJECT_ROOT / "05_tests/L4_SYS5_acceptance"
SOURCE_DIRS = {
    "L1": PROJECT_ROOT / "app/src/test",
    "L2": PROJECT_ROOT / "app/src/androidTest",
}
BUILD_GRADLE = PROJECT_ROOT / "app/build.gradle.kts"
LOGO = PROJECT_ROOT / "05_tests/infra/assets/helmet.png"
LOGO_PX = 56

LEVELS = OrderedDict(
    [
        ("L1", ("SWE.4", "Unit tests", "JVM, no device")),
        ("L2", ("SWE.5", "Integration tests", "On a device or emulator")),
        ("L3", ("SWE.6", "Qualification tests", "Not implemented")),
        ("L4", ("SYS.5", "Acceptance tests", "Human, at a circuit")),
    ]
)

# What each level can and cannot prove. Static, because it is a statement of
# method rather than a measurement.
LEVEL_METHOD = {
    "L1": (
        "Runs on a plain JVM in seconds, so it is where all the arithmetic lives: "
        "lap geometry, corner and braking detection, coaching thresholds, file formats. "
        "It can prove a calculation is right for inputs we thought of. It cannot prove "
        "the result ever reaches the screen."
    ),
    "L2": (
        "Drives the real app on a real Android runtime: fragments, view models, Room, "
        "the foreground service. It proves that wiring exists and survives a lifecycle. "
        "It is slow and needs an emulator, so it is spent on the things a compiler "
        "cannot check."
    ),
    "L3": (
        "Reserved for end-to-end qualification against simulated GPS. Not implemented; "
        "reported here so its absence is visible rather than assumed."
    ),
    "L4": (
        "A human with a car on a circuit. It is the only level that can judge whether the "
        "drawn track looks like the track, whether the corners found are the corners driven, "
        "and whether a lap time matches the driver's own stopwatch."
    ),
}

# Rows in these SRS sections are not testable requirements: one records design
# decisions, the other records what V1 deliberately does not do. Counting them
# would understate coverage as surely as omitting a real requirement overstates it.
EXCLUDED_SECTIONS = ("Architecture decisions", "Out of scope")

STATUS_ORDER = {"FAILED": 0, "MISSING": 1, "SKIPPED": 2, "MANUAL": 3, "PASS": 4}

# Requirements V1 deliberately does not implement. Deferred is not the same as
# untested, and a report that conflates the two teaches the reader to distrust
# all of it.
DEFERRED = "V2-BACKEND"


# ---------------------------------------------------------------------------
# Model
# ---------------------------------------------------------------------------
@dataclass
class TestCase:
    level: str
    classname: str
    name: str
    status: str  # passed | failed | skipped
    time: float
    message: str = ""

    @property
    def simple_class(self) -> str:
        return self.classname.rsplit(".", 1)[-1]


@dataclass
class SourceClass:
    level: str
    name: str
    declared: int = 0
    ignored: bool = False


@dataclass
class Claim:
    requirement: str
    citation: str
    note: str
    status: str = "MISSING"
    level: str = "-"
    detail: str = ""


@dataclass
class Requirement:
    rid: str
    section: str
    text: str
    claims: list = field(default_factory=list)
    scope: str = "V1"
    scope_reason: str = ""

    @property
    def deferred(self) -> bool:
        return self.scope == DEFERRED

    @property
    def status(self) -> str:
        if not self.claims:
            return DEFERRED if self.deferred else "UNCOVERED"
        return min((c.status for c in self.claims), key=lambda s: STATUS_ORDER[s])


# ---------------------------------------------------------------------------
# Parsing
# ---------------------------------------------------------------------------
def parse_junit(directory: Path, level: str):
    """Read every JUnit XML in a directory. Returns (cases, device)."""
    cases, device = [], ""
    if not directory.is_dir():
        return cases, device

    for xml_file in sorted(directory.glob("*.xml")):
        try:
            root = ET.parse(xml_file).getroot()
        except ET.ParseError as exc:  # a truncated file must not be silently skipped
            print(f"[WARN] unreadable: {xml_file.name}: {exc}", file=sys.stderr)
            continue

        for prop in root.iter("property"):
            if prop.get("name") == "device" and prop.get("value"):
                device = prop.get("value")

        for tc in root.iter("testcase"):
            status, message = "passed", ""
            for child in tc:
                if child.tag in ("failure", "error"):
                    status = "failed"
                    message = (child.get("message") or (child.text or "")).strip()
                elif child.tag == "skipped":
                    status = "skipped"
            # An @Ignore'd class is emitted as a single testcase named "null".
            # It is a placeholder for the whole class, not a real test.
            if tc.get("name") == "null":
                status = "skipped"
            cases.append(
                TestCase(
                    level=level,
                    classname=tc.get("classname", "") or "",
                    name=tc.get("name", "") or "",
                    status=status,
                    time=float(tc.get("time") or 0.0),
                    message=message,
                )
            )
    return cases, device


TEST_RE = re.compile(r"^@Test\b")
CLASS_RE = re.compile(r"^\s*(?:internal\s+|private\s+|abstract\s+|open\s+)*class\s+(\w+)")


def parse_source_classes():
    """Count @Test declarations per class, and flag class-level @Ignore.

    This is the only way to see tests hidden behind an ignored class: Gradle
    reports such a class as one skipped entry regardless of how many tests it
    declares, so the XML alone always understates what is not being run.
    """
    found = []
    for level, root in SOURCE_DIRS.items():
        if not root.is_dir():
            continue
        for kt in sorted(root.rglob("*.kt")):
            # A stack of open classes, so that a private helper class nested
            # inside a test class does not swallow the tests declared after it.
            stack = []
            depth = 0
            pending_class = None
            pending_ignore = False
            for line in kt.read_text(encoding="utf-8", errors="replace").splitlines():
                stripped = line.strip()
                if stripped.startswith("@Ignore"):
                    pending_ignore = True
                elif TEST_RE.match(stripped) and stack:
                    stack[-1][0].declared += 1
                elif not stripped.startswith("@") and stripped and not CLASS_RE.match(line):
                    pending_ignore = False

                match = CLASS_RE.match(line)
                if match:
                    pending_class = SourceClass(level=level, name=match.group(1), ignored=pending_ignore)
                    pending_ignore = False

                before = depth
                depth += line.count("{") - line.count("}")
                if pending_class is not None and depth > before:
                    stack.append((pending_class, before))
                    found.append(pending_class)
                    pending_class = None
                while stack and depth <= stack[-1][1]:
                    stack.pop()

    # Simple names must be unique for the report to key on them; if two files
    # share a name, keep the one that actually declares tests.
    classes = {}
    for source in found:
        existing = classes.get(source.name)
        if existing is None or source.declared > existing.declared:
            classes[source.name] = source
    return classes


REQ_ROW_RE = re.compile(r"^\|\s*\*{0,2}([A-Z]{2,4}-\d{2})\*{0,2}\s*\|\s*(.+?)\s*\|")
SECTION_RE = re.compile(r"^##\s+(.+?)\s*$")


def parse_srs():
    """Every requirement ID in the SRS, with its section. This is the denominator."""
    requirements = OrderedDict()
    section = "Unsectioned"
    if not SRS.is_file():
        return requirements
    for line in SRS.read_text(encoding="utf-8").splitlines():
        heading = SECTION_RE.match(line)
        if heading:
            section = re.sub(r"^\d+[a-z]?\.\s*", "", heading.group(1)).strip()
            continue
        row = REQ_ROW_RE.match(line)
        if row:
            rid, text = row.group(1), row.group(2)
            if any(section.startswith(x) for x in EXCLUDED_SECTIONS):
                continue
            if rid not in requirements:
                requirements[rid] = Requirement(rid=rid, section=section, text=text)
    return requirements


L4_RE = re.compile(r"^###\s+([A-Z]{2,5}-\d{2}):\s*(.+?)\s*$")


def parse_l4():
    """Manual acceptance test IDs, grouped by checklist file."""
    checklists = OrderedDict()
    if not L4_DIR.is_dir():
        return checklists
    for md in sorted(L4_DIR.glob("*.md")):
        entries = []
        for line in md.read_text(encoding="utf-8").splitlines():
            match = L4_RE.match(line)
            if match:
                entries.append((match.group(1), match.group(2)))
        if entries:
            checklists[md.name] = entries
    return checklists


def parse_coverage_map():
    claims, errors = [], []
    if not COVERAGE_MAP.is_file():
        return claims, ["coverage-map.tsv not found"]
    for number, raw in enumerate(COVERAGE_MAP.read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        parts = raw.split("\t")
        if len(parts) < 2 or not parts[0].strip() or not parts[1].strip():
            errors.append(f"line {number}: expected 'requirement<TAB>test[<TAB>note]', got {raw!r}")
            continue
        claims.append(
            Claim(
                requirement=parts[0].strip(),
                citation=parts[1].strip(),
                note=parts[2].strip() if len(parts) > 2 else "",
            )
        )
    return claims, errors


def parse_scope_map(requirements):
    """Apply scope decisions to requirements. Returns a list of errors.

    A section line covers everything in that SRS section; a requirement line
    always wins over it, so an exception stays visible on its own line instead
    of being buried in a sweep.
    """
    errors = []
    if not SCOPE_MAP.is_file():
        return errors

    by_section = {}
    for req in requirements.values():
        by_section.setdefault(req.section, []).append(req)

    section_lines, id_lines = [], []
    for number, raw in enumerate(SCOPE_MAP.read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        parts = raw.split("\t")
        if len(parts) < 2 or not parts[0].strip() or not parts[1].strip():
            errors.append(f"scope-map line {number}: expected 'scope<TAB>target[<TAB>reason]'")
            continue
        scope, target = parts[0].strip(), parts[1].strip()
        reason = parts[2].strip() if len(parts) > 2 else ""
        if scope not in (DEFERRED, "V1"):
            errors.append(f"scope-map line {number}: unknown scope {scope!r}")
            continue
        (section_lines if target.startswith("@") else id_lines).append(
            (number, scope, target, reason)
        )

    # Sections first, so that a requirement line can override one.
    for number, scope, target, reason in section_lines:
        name = target[1:]
        if name not in by_section:
            errors.append(f"scope-map line {number}: no SRS section named {name!r}")
            continue
        for req in by_section[name]:
            req.scope, req.scope_reason = scope, reason

    for number, scope, target, reason in id_lines:
        req = requirements.get(target)
        if req is None:
            errors.append(f"scope-map line {number}: no such requirement {target}")
            continue
        req.scope, req.scope_reason = scope, reason

    # The guard that stops this file becoming a rug: a requirement cannot be
    # deferred and simultaneously proven by a test that runs today.
    for req in requirements.values():
        if req.deferred and any(c.status == "PASS" for c in req.claims):
            passing = ", ".join(sorted(c.citation for c in req.claims if c.status == "PASS"))
            errors.append(
                f"{req.rid} is marked {DEFERRED} but has a passing test ({passing}). "
                "Either the scope note is stale, or this was built after all."
            )
    return errors


# ---------------------------------------------------------------------------
# Resolution: turn claims into verdicts backed by real results
# ---------------------------------------------------------------------------
def resolve(claims, cases, source_classes, l4_ids):
    by_class = {}
    for case in cases:
        by_class.setdefault(case.simple_class, []).append(case)

    for claim in claims:
        citation = claim.citation

        if citation.startswith("L4:"):
            test_id = citation[3:]
            claim.level = "L4"
            if test_id in l4_ids:
                claim.status = "MANUAL"
                claim.detail = "Awaiting a human run"
            else:
                claim.status = "MISSING"
                claim.detail = "No such test ID in the L4 checklists"
            continue

        class_name, _, method = citation.partition(".")
        found = by_class.get(class_name, [])
        source = source_classes.get(class_name)
        claim.level = found[0].level if found else (source.level if source else "-")

        if not found:
            claim.status = "MISSING"
            claim.detail = (
                "Class declared in source but absent from the results"
                if source
                else "No such test class anywhere in the codebase"
            )
            continue

        if method:
            matches = [c for c in found if c.name == method]
            if not matches:
                claim.status = "MISSING"
                claim.detail = f"{class_name} ran, but has no test named {method}"
                continue
        else:
            matches = found

        if any(c.status == "failed" for c in matches):
            claim.status = "FAILED"
            claim.detail = "The cited test failed"
        elif all(c.status == "skipped" for c in matches):
            claim.status = "SKIPPED"
            hidden = source.declared if source and source.ignored else 0
            claim.detail = (
                f"Class is @Ignore'd — {hidden} declared test(s) never ran"
                if hidden
                else "The cited test did not run"
            )
        else:
            claim.status = "PASS"
            ran = len([c for c in matches if c.status == "passed"])
            claim.detail = f"{ran} test(s) passed"
    return claims


# ---------------------------------------------------------------------------
# Rendering
# ---------------------------------------------------------------------------
CSS = """
:root{--bmw:#1C69D4;--gold:#C9A227;--ink:#1a1d21;--muted:#5b6570;--line:#dfe3e8;
--pass:#2e7d32;--fail:#c62828;--warn:#e07b00;--manual:#5a6acf;--bg:#f6f7f9}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--ink);
font:15px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif}
.wrap{max-width:1080px;margin:0 auto;padding:0 24px 72px}
header{background:linear-gradient(135deg,#0f2a52,var(--bmw));color:#fff;padding:36px 0 30px;margin-bottom:28px}
header .wrap{padding-bottom:0}
.brand{display:flex;align-items:flex-start;gap:18px}
.brand img{flex:none;display:block}
.brand-text{min-width:0}
h1{margin:0 0 6px;font-size:26px;letter-spacing:.2px}
.sub{opacity:.85;font-size:14px}
.meta{display:flex;flex-wrap:wrap;gap:8px 28px;margin-top:18px;font-size:13px;opacity:.92}
.meta b{font-weight:600;opacity:.7;font-weight:400}
h2{font-size:19px;margin:38px 0 12px;padding-bottom:7px;border-bottom:2px solid var(--line)}
h3{font-size:15px;margin:22px 0 8px}
p{margin:9px 0}
.note{color:var(--muted);font-size:13.5px}
table{border-collapse:collapse;width:100%;background:#fff;
box-shadow:0 1px 2px rgba(0,0,0,.06);border-radius:6px;overflow:hidden}
th,td{padding:9px 12px;text-align:left;border-bottom:1px solid var(--line);font-size:13.5px;vertical-align:top}
th{background:#eef1f5;font-weight:600;font-size:12.5px;text-transform:uppercase;letter-spacing:.4px;color:var(--muted)}
tr:last-child td{border-bottom:none}
td.num,th.num{text-align:right;font-variant-numeric:tabular-nums}
.verdict{border-radius:8px;padding:16px 20px;margin:4px 0 8px;font-size:15px;background:#fff;
border-left:5px solid var(--pass);box-shadow:0 1px 2px rgba(0,0,0,.06)}
.verdict.bad{border-left-color:var(--fail)}
.verdict.warn{border-left-color:var(--warn)}
.verdict b{font-size:17px}
.tag{display:inline-block;padding:1px 8px;border-radius:11px;font-size:11.5px;font-weight:600;
letter-spacing:.3px;white-space:nowrap}
.PASS{background:#e6f4ea;color:var(--pass)}
.FAILED{background:#fdecea;color:var(--fail)}
.SKIPPED{background:#fff4e0;color:var(--warn)}
.MISSING{background:#fdecea;color:var(--fail)}
.MANUAL{background:#eceefc;color:var(--manual)}
.UNCOVERED{background:#eceff1;color:var(--muted)}
.V2-BACKEND{background:#e8f0fe;color:#1a56b0}
details{background:#fff;border:1px solid var(--line);border-radius:6px;margin:8px 0}
details[open]{box-shadow:0 1px 3px rgba(0,0,0,.07)}
summary{cursor:pointer;padding:10px 14px;font-size:13.5px;font-weight:600;list-style:none}
summary::-webkit-details-marker{display:none}
summary:before{content:"\\25B8";display:inline-block;margin-right:9px;color:var(--muted);transition:none}
details[open] summary:before{content:"\\25BE"}
details .body{padding:0 14px 12px}
details table{box-shadow:none;border:1px solid var(--line)}
code,.mono{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:12.5px}
.kpi{display:flex;flex-wrap:wrap;gap:14px;margin:14px 0 4px}
.kpi div{flex:1 1 150px;background:#fff;border-radius:6px;padding:13px 16px;
box-shadow:0 1px 2px rgba(0,0,0,.06);border-top:3px solid var(--bmw)}
.kpi .n{font-size:25px;font-weight:600;line-height:1.15}
.kpi .l{font-size:12px;color:var(--muted);text-transform:uppercase;letter-spacing:.4px}
.kpi div.alert{border-top-color:var(--warn)}
.kpi div.bad{border-top-color:var(--fail)}
footer{margin-top:44px;padding-top:18px;border-top:1px solid var(--line);
color:var(--muted);font-size:12.5px}
"""


def esc(text) -> str:
    return html.escape(str(text), quote=True)


def tag(status: str) -> str:
    return f'<span class="tag {status}">{status}</span>'


def default_title(version: str) -> str:
    """The name both reports carry.

    "Trillian" is the programme, "Driving Coach" the product. Leading with the
    programme matters beyond the heading: this string is also the <title>, so
    it becomes the browser tab and the filename a reader gets when they print
    the report to PDF and mail it on.
    """
    return f"Trillian · Driving Coach v{version} — Test Report"


def logo_data_uri(path: Path = LOGO) -> str:
    """Return the helmet as a base64 data URI, or "" if it is unavailable.

    Embedded rather than linked because package-release.sh copies only the HTML
    file into the release directory -- a relative <img src> would pass our
    no-external-URL check and still render as a broken image on the one machine
    that matters, the reader's.

    Never raises. A test report that fails to generate because an ornament is
    missing would be a worse defect than the missing ornament.
    """
    try:
        return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode("ascii")
    except OSError:
        return ""


def render(ctx) -> str:
    out = []
    w = out.append

    w("<!DOCTYPE html>")
    w('<html lang="en"><head><meta charset="utf-8">')
    w('<meta name="viewport" content="width=device-width,initial-scale=1">')
    w(f"<title>{esc(ctx['title'])}</title>")
    w(f"<style>{CSS}</style></head><body>")

    # ---- header ----
    # The helmet is the mark the reader already saw on the app's splash screen,
    # so the report and the product are visibly the same thing. Width and
    # height are stated explicitly and equal: incident #11 was this artwork
    # deformed by a non-uniform scale, and it is not going to happen again in
    # the document that exists to demonstrate we test properly.
    logo = ctx.get("logo", "")
    w('<header><div class="wrap"><div class="brand">')
    if logo:
        w(f'<img src="{logo}" width="{LOGO_PX}" height="{LOGO_PX}" alt="">')
    w('<div class="brand-text">')
    w(f"<h1>{esc(ctx['title'])}</h1>")
    w('<div class="sub">Automated evidence, generated from test results — not written by hand.</div>')
    w('<div class="meta">')
    for label, value in ctx["meta"]:
        w(f"<span><b>{esc(label)}:</b> {esc(value)}</span>")
    w("</div></div></div></div></header>")

    w('<div class="wrap">')

    # ---- verdict ----
    totals = ctx["totals"]
    if totals["failed"]:
        cls, headline = "bad", f"{totals['failed']} test(s) failed"
    elif totals["hidden"] or ctx["unbacked"]:
        cls, headline = "warn", "All executed tests passed, with caveats below"
    else:
        cls, headline = "", "All executed tests passed"
    w(f'<div class="verdict {cls}"><b>{esc(headline)}</b><br>')
    w(
        f"{totals['executed']} of {totals['declared']} declared automated tests ran; "
        f"{totals['passed']} passed, {totals['failed']} failed, {totals['skipped_entries']} skipped."
    )
    if totals["hidden"]:
        w(
            f"<br><strong>{totals['hidden']} declared test(s) never ran</strong> because their class "
            f"is annotated <code>@Ignore</code>. Gradle reports such a class as a single skipped "
            f"entry, so this number is invisible in a normal build log."
        )
    w("</div>")

    # ---- KPIs ----
    w('<div class="kpi">')
    w(f'<div><div class="n">{totals["executed"]}</div><div class="l">Tests executed</div></div>')
    w(f'<div><div class="n">{totals["passed"]}</div><div class="l">Passed</div></div>')
    bad = " bad" if totals["failed"] else ""
    w(f'<div class="{bad.strip()}"><div class="n">{totals["failed"]}</div><div class="l">Failed</div></div>')
    alert = " alert" if totals["hidden"] else ""
    w(f'<div class="{alert.strip()}"><div class="n">{totals["hidden"]}</div><div class="l">Never ran</div></div>')
    w(f'<div><div class="n">{ctx["v1_covered"]}/{ctx["v1_total"]}</div>'
      f'<div class="l">V1 requirements claimed</div></div>')
    w(f'<div><div class="n">{len(ctx["deferred"])}</div>'
      f'<div class="l">Deferred to V2</div></div>')
    w("</div>")

    # ---- summary by level ----
    w("<h2>1. Results by test level</h2>")
    w('<p class="note">Levels follow Automotive SPICE. "Declared" counts <code>@Test</code> '
      "annotations in the source; \"executed\" counts what the run actually reported. A gap "
      "between the two is the number of tests that exist but are switched off.</p>")
    w("<table><tr><th>Level</th><th>ASPICE</th><th>Scope</th>"
      '<th class="num">Declared</th><th class="num">Executed</th><th class="num">Passed</th>'
      '<th class="num">Failed</th><th class="num">Never ran</th><th>Status</th></tr>')
    for level, row in ctx["levels"].items():
        aspice, name, where = LEVELS[level]
        w(
            f"<tr><td><b>{level}</b></td><td>{aspice}</td><td>{esc(name)}<br>"
            f'<span class="note">{esc(where)}</span></td>'
            f'<td class="num">{row["declared"]}</td><td class="num">{row["executed"]}</td>'
            f'<td class="num">{row["passed"]}</td><td class="num">{row["failed"]}</td>'
            f'<td class="num">{row["hidden"]}</td><td>{row["status"]}</td></tr>'
        )
    w("</table>")

    # ---- method ----
    w("<h2>2. How we test</h2>")
    w('<p class="note">What each level can prove, and what it cannot. A test at the wrong '
      "level is a test that will pass while the product is broken.</p>")
    for level, (aspice, name, _) in LEVELS.items():
        w(f"<h3>{level} — {esc(name)} <span class='note'>({aspice})</span></h3>")
        w(f"<p>{esc(LEVEL_METHOD[level])}</p>")
    if ctx["deferred"]:
        w("<h3>What V1 does not build</h3>")
        w(f'<p>V1 is deliberately offline-first: it records, detects laps, analyses and coaches '
          f"entirely on the phone. {len(ctx['deferred'])} requirements in the SRS describe a "
          "server, Firebase authentication or a hosted model, none of which V1 deploys. They are "
          "marked <span class=\"tag V2-BACKEND\">V2-BACKEND</span> throughout this report and "
          "counted separately, never removed. A requirement marked deferred that turns out to "
          "have a passing test is reported as an error, so this cannot be used to quietly retire "
          "anything that was actually built.</p>")

    # ---- failures ----
    w("<h2>3. Failures</h2>")
    if not ctx["failures"]:
        w("<p>No test failed in this run.</p>")
    else:
        w("<table><tr><th>Level</th><th>Test</th><th>Message</th></tr>")
        for case in ctx["failures"]:
            w(f'<tr><td>{case.level}</td><td class="mono">{esc(case.simple_class)}.{esc(case.name)}</td>'
              f'<td class="mono">{esc(case.message[:400])}</td></tr>')
        w("</table>")

    # ---- full inventory ----
    w("<h2>4. Every automated test in this run</h2>")
    w('<p class="note">The complete inventory, listed before any claim is made about it, so that '
      "the coverage in section 6 is read against tests you have already seen rather than taken on "
      "trust.</p>")
    for level in ("L1", "L2"):
        classes = ctx["by_class"].get(level, {})
        if not classes:
            continue
        w(f"<h3>{level} — {len(classes)} classes</h3>")
        for class_name in sorted(classes):
            cases = classes[class_name]
            failed = sum(1 for c in cases if c.status == "failed")
            skipped = sum(1 for c in cases if c.status == "skipped")
            source = ctx["source_classes"].get(class_name)
            if source and source.ignored:
                label = f"@Ignore — {source.declared} test(s) never ran"
            else:
                label = f"{len(cases)} test(s)"
                if failed:
                    label += f", {failed} failed"
                if skipped:
                    label += f", {skipped} skipped"
            w(f"<details><summary>{esc(class_name)} <span class='note'>— {esc(label)}</span>"
              "</summary><div class='body'><table>")
            w('<tr><th>Test</th><th>Status</th><th class="num">Time (s)</th></tr>')
            for case in sorted(cases, key=lambda c: c.name):
                status = {"passed": "PASS", "failed": "FAILED", "skipped": "SKIPPED"}[case.status]
                name = case.name if case.name != "null" else "(whole class skipped)"
                w(f'<tr><td class="mono">{esc(name)}</td><td>{tag(status)}</td>'
                  f'<td class="num">{case.time:.3f}</td></tr>')
            w("</table></div></details>")

    # ---- manual, last ----
    w("<h2>5. Manual acceptance tests (L4)</h2>")
    w('<div class="verdict warn"><b>These tests are not automated and were not run.</b><br>'
      "A human must execute them with a car on a circuit and record the result. Nothing in "
      "else in this report covers what they check: whether the app tells the truth about a "
      "real drive.</div>")
    w(f'<p class="note">{ctx["l4_total"]} checks across {len(ctx["l4"])} checklists in '
      "<code>05_tests/L4_SYS5_acceptance/</code>.</p>")
    for filename, entries in ctx["l4"].items():
        w(f"<details><summary>{esc(filename)} <span class='note'>— {len(entries)} checks</span>"
          "</summary><div class='body'><table>")
        w("<tr><th>ID</th><th>Check</th></tr>")
        for test_id, title in entries:
            w(f'<tr><td class="mono">{esc(test_id)}</td><td>{esc(title)}</td></tr>')
        w("</table></div></details>")

    # ---- coverage ----
    w("<h2>6. Requirements coverage</h2>")
    w('<p class="note">Every requirement in the SRS, and the tests that claim it. '
      "Claims come from <code>05_tests/coverage-map.tsv</code> and are checked against the run: "
      "a claim naming a test that did not run, or no longer exists, is reported rather than "
      "counted. Expand a row to see the individual tests.</p>")
    w(f'<p class="note"><b>{ctx["v1_covered"]} of {ctx["v1_total"]} V1 requirements '
      f'({ctx["v1_pct"]}%) have at least one automated claim</b>, and '
      f'{ctx["covered_reqs"]} of {ctx["total_reqs"]} ({ctx["coverage_pct"]}%) counting the '
      f'{len(ctx["deferred"])} deferred to V2. Both numbers are printed because either one alone '
      "misleads: the first flatters V1 by ignoring what was never built, the second punishes it "
      "for a scope decision that was made on purpose. Deferred requirements keep their row, their "
      "reason and their count — see section 7.</p>")
    w('<p class="note">The denominator is every requirement row in the SRS, excluding the '
      "architecture-decision and out-of-scope sections, which record intent rather than "
      "behaviour. No requirement is removed for being inconvenient.</p>")
    w("<table><tr><th>Status</th><th>Meaning</th></tr>")
    for status, meaning in [
        ("PASS", "The cited test ran and passed"),
        ("FAILED", "The cited test ran and failed"),
        ("SKIPPED", "The test exists but did not run — the claim is not backed by evidence"),
        ("MISSING", "The cited test was not found — the claim has rotted"),
        ("MANUAL", "An L4 claim; only a human at a circuit can discharge it"),
        ("UNCOVERED", "No test claims this requirement"),
        (DEFERRED, "V1 does not implement this; it needs a server, Firebase or a hosted model"),
    ]:
        w(f"<tr><td>{tag(status)}</td><td>{esc(meaning)}</td></tr>")
    w("</table>")

    for section, reqs in ctx["sections"].items():
        in_v1 = [r for r in reqs if not r.deferred]
        counted = sum(1 for r in in_v1 if r.claims)
        deferred_here = len(reqs) - len(in_v1)
        if not in_v1:
            label = f"all {deferred_here} deferred to V2"
        else:
            label = f"{counted}/{len(in_v1)} V1 requirements claimed"
            if deferred_here:
                label += f", {deferred_here} deferred to V2"
        w(f"<h3>{esc(section)} <span class='note'>— {esc(label)}</span></h3>")
        w("<table><tr><th>ID</th><th>Requirement</th><th>Level</th><th>Status</th><th>Evidence</th></tr>")
        for req in reqs:
            levels = sorted({c.level for c in req.claims if c.level != "-"})
            w(
                f'<tr><td class="mono"><b>{esc(req.rid)}</b></td><td>{esc(req.text)}</td>'
                f'<td>{esc(" + ".join(levels) or "—")}</td><td>{tag(req.status)}</td><td>'
            )
            if req.deferred and not req.claims:
                w(f'<span class="note">{esc(req.scope_reason or "Deferred to V2")}</span>')
            elif not req.claims:
                w('<span class="note">No claim recorded</span>')
            else:
                w("<details><summary>"
                  f"{len(req.claims)} claim(s)</summary><div class='body'><table>")
                w("<tr><th>Test</th><th>Status</th><th>Detail</th><th>Note</th></tr>")
                for claim in sorted(req.claims, key=lambda c: c.citation):
                    w(
                        f'<tr><td class="mono">{esc(claim.citation)}</td>'
                        f"<td>{tag(claim.status)}</td><td>{esc(claim.detail)}</td>"
                        f'<td class="note">{esc(claim.note)}</td></tr>'
                    )
                w("</table></div></details>")
            w("</td></tr>")
        w("</table>")

    # ---- gaps ----
    w("<h2>7. Gaps and caveats</h2>")
    if ctx["ignored_classes"]:
        w("<h3>Test classes that are switched off</h3>")
        w('<p class="note">These classes are annotated <code>@Ignore</code>. They compile, they '
          "appear in the codebase, and they prove nothing.</p>")
        w('<table><tr><th>Class</th><th>Level</th><th class="num">Tests never run</th></tr>')
        for source in ctx["ignored_classes"]:
            w(f'<tr><td class="mono">{esc(source.name)}</td><td>{source.level}</td>'
              f'<td class="num">{source.declared}</td></tr>')
        w("</table>")
    if ctx["unbacked"]:
        w("<h3>Coverage claims not backed by a passing test</h3>")
        w('<table><tr><th>Requirement</th><th>Claim</th><th>Status</th><th>Detail</th></tr>')
        for claim in ctx["unbacked"]:
            w(f'<tr><td class="mono">{esc(claim.requirement)}</td>'
              f'<td class="mono">{esc(claim.citation)}</td><td>{tag(claim.status)}</td>'
              f"<td>{esc(claim.detail)}</td></tr>")
        w("</table>")
    if ctx["deferred"]:
        w("<h3>Deferred to V2 — needs a backend</h3>")
        w(f'<p class="note">{len(ctx["deferred"])} requirements describe software V1 does not '
          "build: a server, Firebase authentication, or a hosted model. They cannot be tested "
          "because they do not exist, which is a scope decision rather than a testing gap. They "
          "are listed here in full, with the reason recorded per requirement, so the distinction "
          "is visible rather than asserted. The rule applied is the subject of the sentence: "
          "<em>the backend shall</em> is deferred, <em>the app shall</em> is not — the client half "
          "of a network feature is testable against a fake server, and some of it already is.</p>")
        w("<table><tr><th>ID</th><th>Section</th><th>Requirement</th><th>Why deferred</th></tr>")
        for req in ctx["deferred"]:
            w(f'<tr><td class="mono">{esc(req.rid)}</td><td>{esc(req.section)}</td>'
              f'<td>{esc(req.text)}</td><td class="note">{esc(req.scope_reason)}</td></tr>')
        w("</table>")

    if ctx["uncovered"]:
        w("<h3>Requirements with no automated claim</h3>")
        w(f'<p class="note">{len(ctx["uncovered"])} of {ctx["v1_total"]} V1 requirements carry no '
          "automated test. These are not deferred: they describe software that ships today and "
          "is not covered. Every one is "
          'listed and marked <span class="tag UNCOVERED">UNCOVERED</span> in section 6, so none of '
          "them can hide. The count by area:</p>")
        by_section = OrderedDict()
        for req in ctx["uncovered"]:
            by_section.setdefault(req.section, []).append(req.rid)
        w('<table><tr><th>Area</th><th class="num">Uncovered</th><th>Requirements</th></tr>')
        for section, rids in by_section.items():
            w(f'<tr><td>{esc(section)}</td><td class="num">{len(rids)}</td>'
              f'<td class="mono">{esc(", ".join(rids))}</td></tr>')
        w("</table>")
    if ctx["map_errors"]:
        w("<h3>Malformed lines in coverage-map.tsv</h3>")
        w("<table><tr><th>Problem</th></tr>")
        for err in ctx["map_errors"]:
            w(f'<tr><td class="mono">{esc(err)}</td></tr>')
        w("</table>")

    if ctx["l4"]:
        w('<div class="verdict warn" style="margin-top:34px"><b>Before this build is trusted on '
          f'track, a human still has to run the {ctx["l4_total"]} manual checks in section 5.</b>'
          "<br>Nothing above proves that the lap time on the screen matches the driver's own "
          "stopwatch, or that the drawn map is the circuit they just drove. Only a person in the "
          "car can do that.</div>")

    w("<footer>")
    w(f"Generated by <code>{esc(Path(__file__).name)}</code> at {esc(ctx['generated'])}. ")
    w("Every figure is derived from JUnit XML, Kotlin source and "
      "<code>05_tests/coverage-map.tsv</code>; none of it is written by hand.")
    w("</footer></div></body></html>")
    return "\n".join(out) + "\n"


# ---------------------------------------------------------------------------
# Assembly
# ---------------------------------------------------------------------------
def git(*args, default=""):
    try:
        return subprocess.run(
            ["git", *args], cwd=PROJECT_ROOT, capture_output=True, text=True, timeout=10
        ).stdout.strip() or default
    except Exception:
        return default


def app_version() -> str:
    if not BUILD_GRADLE.is_file():
        return "unknown"
    match = re.search(r'val\s+appVersionName\s*=\s*"([^"]+)"', BUILD_GRADLE.read_text(encoding="utf-8"))
    return match.group(1) if match else "unknown"


def build_context(args):
    l1_cases, _ = parse_junit(Path(args.l1_results), "L1")
    l2_cases, device = parse_junit(Path(args.l2_results), "L2")
    cases = l1_cases + l2_cases

    source_classes = parse_source_classes()
    requirements = parse_srs()
    claims, map_errors = parse_coverage_map()
    l4 = parse_l4()
    l4_ids = {tid for entries in l4.values() for tid, _ in entries}

    resolve(claims, cases, source_classes, l4_ids)

    for claim in claims:
        req = requirements.get(claim.requirement)
        if req is None:
            map_errors.append(
                f"{claim.requirement}: no such requirement in the SRS (cited by {claim.citation})"
            )
            continue
        req.claims.append(claim)

    # Scope is applied after claims, because the contradiction guard needs to
    # see whether a deferred requirement has a test that passes.
    map_errors.extend(parse_scope_map(requirements))

    # Per-level roll-up. "declared" counts @Test in source; "executed" counts
    # what the run reported. The difference is what is switched off.
    levels = OrderedDict()
    ran_classes = {c.simple_class for c in cases}
    for level in LEVELS:
        level_cases = [c for c in cases if c.level == level]
        declared = sum(s.declared for s in source_classes.values() if s.level == level)
        hidden = sum(
            s.declared
            for s in source_classes.values()
            if s.level == level and (s.ignored or s.name not in ran_classes)
        )
        executed = sum(1 for c in level_cases if c.name != "null")
        passed = sum(1 for c in level_cases if c.status == "passed")
        failed = sum(1 for c in level_cases if c.status == "failed")
        skipped = sum(1 for c in level_cases if c.status == "skipped")
        if level == "L3":
            status = "Not implemented"
        elif level == "L4":
            status = "Manual — see section 5"
            declared = sum(len(e) for e in l4.values())
            executed = passed = failed = skipped = hidden = 0
        elif not level_cases:
            status = "Not executed"
        elif failed:
            status = "FAILED"
        elif hidden:
            status = "Passed, with gaps"
        else:
            status = "Passed"
        levels[level] = {
            "declared": declared,
            "executed": executed,
            "passed": passed,
            "failed": failed,
            "skipped": skipped,
            "hidden": hidden,
            "status": status,
        }

    totals = {
        "declared": levels["L1"]["declared"] + levels["L2"]["declared"],
        "executed": levels["L1"]["executed"] + levels["L2"]["executed"],
        "passed": levels["L1"]["passed"] + levels["L2"]["passed"],
        "failed": levels["L1"]["failed"] + levels["L2"]["failed"],
        "skipped_entries": levels["L1"]["skipped"] + levels["L2"]["skipped"],
        "hidden": levels["L1"]["hidden"] + levels["L2"]["hidden"],
    }

    sections = OrderedDict()
    for req in requirements.values():
        sections.setdefault(req.section, []).append(req)

    deferred = [r for r in requirements.values() if r.deferred]
    v1_reqs = [r for r in requirements.values() if not r.deferred]
    v1_covered = sum(1 for r in v1_reqs if r.claims)

    by_class = {}
    for case in cases:
        by_class.setdefault(case.level, {}).setdefault(case.simple_class, []).append(case)

    generated = args.source_date or datetime.now(timezone.utc).astimezone().strftime(
        "%Y-%m-%d %H:%M:%S %Z"
    )
    version = app_version()

    return {
        "title": args.title or default_title(version),
        "logo": logo_data_uri(),
        "generated": generated,
        "meta": [
            ("Version", f"v{version}"),
            ("Commit", git("rev-parse", "--short", "HEAD", default="unknown")),
            ("Branch", git("rev-parse", "--abbrev-ref", "HEAD", default="unknown")),
            ("L2 device", device or "not run"),
            ("Generated", generated),
        ],
        "levels": levels,
        "totals": totals,
        "sections": sections,
        "uncovered": [r for r in requirements.values() if not r.claims and not r.deferred],
        "deferred": deferred,
        "v1_total": len(v1_reqs),
        "v1_covered": v1_covered,
        "v1_pct": round(100.0 * v1_covered / max(len(v1_reqs), 1)),
        "covered_reqs": sum(1 for r in requirements.values() if r.claims),
        "total_reqs": len(requirements),
        "coverage_pct": round(
            100.0 * sum(1 for r in requirements.values() if r.claims) / max(len(requirements), 1)
        ),
        "unbacked": sorted(
            [c for c in claims if c.status in ("SKIPPED", "MISSING", "FAILED")],
            key=lambda c: (c.requirement, c.citation),
        ),
        "ignored_classes": sorted(
            [s for s in source_classes.values() if s.ignored], key=lambda s: s.name
        ),
        "failures": sorted(
            [c for c in cases if c.status == "failed"], key=lambda c: (c.classname, c.name)
        ),
        "by_class": by_class,
        "source_classes": source_classes,
        "map_errors": map_errors,
        "l4": l4,
        "l4_total": sum(len(e) for e in l4.values()),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--output", default=str(PROJECT_ROOT / "05_tests/reports/TEST_REPORT.html"))
    parser.add_argument("--l1-results", default=str(DEFAULT_L1_RESULTS))
    parser.add_argument("--l2-results", default=str(DEFAULT_L2_RESULTS))
    parser.add_argument("--title", default="")
    parser.add_argument(
        "--source-date",
        default=os.environ.get("REPORT_SOURCE_DATE", ""),
        help="Pin the generation timestamp so two runs can be diffed byte for byte",
    )
    args = parser.parse_args()

    context = build_context(args)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(render(context), encoding="utf-8")

    totals = context["totals"]
    print(f"[INFO] HTML report: {output}")
    print(
        f"[INFO] {totals['executed']}/{totals['declared']} tests executed, "
        f"{totals['passed']} passed, {totals['failed']} failed, {totals['hidden']} never ran"
    )
    if context["unbacked"]:
        print(f"[WARN] {len(context['unbacked'])} coverage claim(s) not backed by a passing test")
    if context["map_errors"]:
        print(f"[WARN] {len(context['map_errors'])} problem(s) in the coverage or scope map")
        for problem in context["map_errors"][:10]:
            print(f"       {problem}")
    print(
        f"[INFO] {context['v1_covered']}/{context['v1_total']} V1 requirements claimed "
        f"({context['v1_pct']}%), {len(context['deferred'])} deferred to V2"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
