#!/usr/bin/env python3
"""Tests for generate-html-report.py.

The report is the artefact people will trust without reading the code, so the
things worth testing are the ones that could make it lie: miscounting declared
tests, missing a class that never ran, or marking a rotted coverage claim as
passing.

Run with:
    python3 -m unittest discover -s 05_tests/infra/scripts -p 'test_*.py'
"""

import importlib.util
import re
import struct
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "generate-html-report.py"

_spec = importlib.util.spec_from_file_location("report_generator", SCRIPT)
gen = importlib.util.module_from_spec(_spec)
sys.modules["report_generator"] = gen
_spec.loader.exec_module(gen)


def write(directory: Path, name: str, text: str) -> Path:
    path = directory / name
    path.write_text(text, encoding="utf-8")
    return path


class SourceParsingTest(unittest.TestCase):
    """@Test counting must survive real Kotlin, not just tidy Kotlin."""

    def parse(self, source: str, level: str = "L1"):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root, "Sample.kt", source)
            original = dict(gen.SOURCE_DIRS)
            gen.SOURCE_DIRS.clear()
            gen.SOURCE_DIRS[level] = root
            try:
                return gen.parse_source_classes()
            finally:
                gen.SOURCE_DIRS.clear()
                gen.SOURCE_DIRS.update(original)

    def test_counts_tests_in_a_plain_class(self):
        classes = self.parse(
            """
            class ThingTest {
                @Test fun a() {}
                @Test
                fun b() {}
            }
            """
        )
        self.assertEqual(classes["ThingTest"].declared, 2)

    def test_nested_helper_class_does_not_steal_the_tests_after_it(self):
        # This is a real shape in this codebase: a fake collaborator declared
        # near the top of the test class. A naive parser attributes every test
        # below it to the fake, and the totals silently stop matching the run.
        classes = self.parse(
            """
            class StoreTest {
                private class FakeStore : Store {
                    override fun get() = null
                }
                @Test fun a() {}
                @Test fun b() {}
                @Test fun c() {}
            }
            """
        )
        self.assertEqual(classes["StoreTest"].declared, 3)
        self.assertEqual(classes["FakeStore"].declared, 0)

    def test_class_level_ignore_is_detected(self):
        classes = self.parse(
            """
            @RunWith(AndroidJUnit4::class)
            @Ignore("Flaky on CI")
            class SwitchedOffTest {
                @Test fun a() {}
                @Test fun b() {}
            }
            """
        )
        self.assertTrue(classes["SwitchedOffTest"].ignored)
        self.assertEqual(classes["SwitchedOffTest"].declared, 2)

    def test_annotation_starting_with_test_is_not_a_test(self):
        classes = self.parse(
            """
            @TestInstallIn(components = [SingletonComponent::class])
            class TestModule {
                @Test fun real() {}
            }
            """
        )
        self.assertEqual(classes["TestModule"].declared, 1)

    def test_a_later_class_does_not_inherit_the_previous_ignore(self):
        classes = self.parse(
            """
            @Ignore
            class OffTest {
                @Test fun a() {}
            }

            class OnTest {
                @Test fun b() {}
            }
            """
        )
        self.assertTrue(classes["OffTest"].ignored)
        self.assertFalse(classes["OnTest"].ignored)


class JUnitParsingTest(unittest.TestCase):
    def parse(self, xml: str):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root, "TEST-sample.xml", xml)
            return gen.parse_junit(root, "L2")

    def test_reads_outcomes_and_device(self):
        cases, device = self.parse(
            """<?xml version='1.0'?>
            <testsuite name="s" tests="3">
              <properties><property name="device" value="Pixel(AVD)"/></properties>
              <testcase name="ok" classname="com.x.ATest" time="0.5"/>
              <testcase name="bad" classname="com.x.ATest" time="0.2">
                <failure message="boom">trace</failure>
              </testcase>
              <testcase name="off" classname="com.x.ATest" time="0"><skipped/></testcase>
            </testsuite>"""
        )
        self.assertEqual(device, "Pixel(AVD)")
        self.assertEqual({c.name: c.status for c in cases},
                         {"ok": "passed", "bad": "failed", "off": "skipped"})
        self.assertEqual([c.message for c in cases if c.status == "failed"], ["boom"])

    def test_ignored_class_placeholder_is_treated_as_skipped(self):
        # Gradle emits exactly one testcase named "null" for an @Ignore'd class,
        # whatever the number of tests inside it. Counting that as a test run is
        # how a report ends up claiming coverage that does not exist.
        cases, _ = self.parse(
            """<?xml version='1.0'?>
            <testsuite name="s" tests="1">
              <testcase name="null" classname="com.x.OffTest" time="0"><skipped/></testcase>
            </testsuite>"""
        )
        self.assertEqual(cases[0].status, "skipped")
        self.assertEqual(cases[0].name, "null")

    def test_unreadable_file_does_not_abort_the_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root, "TEST-broken.xml", "<testsuite>truncated")
            write(root, "TEST-good.xml",
                  '<testsuite name="s"><testcase name="a" classname="c.B" time="0"/></testsuite>')
            cases, _ = gen.parse_junit(root, "L1")
        self.assertEqual([c.name for c in cases], ["a"])


class ClaimResolutionTest(unittest.TestCase):
    """Each of the four verdicts a claim can receive."""

    def setUp(self):
        self.cases = [
            gen.TestCase("L1", "com.x.GoodTest", "works", "passed", 0.1),
            gen.TestCase("L1", "com.x.GoodTest", "also_works", "passed", 0.1),
            gen.TestCase("L1", "com.x.BadTest", "breaks", "failed", 0.1, "boom"),
            gen.TestCase("L2", "com.x.OffTest", "null", "skipped", 0.0),
        ]
        self.sources = {
            "GoodTest": gen.SourceClass("L1", "GoodTest", 2),
            "BadTest": gen.SourceClass("L1", "BadTest", 1),
            "OffTest": gen.SourceClass("L2", "OffTest", 7, ignored=True),
        }
        self.l4 = {"BRD-01"}

    def resolve(self, citation):
        claim = gen.Claim("XX-01", citation, "")
        gen.resolve([claim], self.cases, self.sources, self.l4)
        return claim

    def test_passing_class(self):
        claim = self.resolve("GoodTest")
        self.assertEqual(claim.status, "PASS")
        self.assertEqual(claim.level, "L1")

    def test_named_method(self):
        self.assertEqual(self.resolve("GoodTest.works").status, "PASS")

    def test_failing_test_is_not_reported_as_covered(self):
        self.assertEqual(self.resolve("BadTest").status, "FAILED")

    def test_ignored_class_is_skipped_and_says_how_many_are_hidden(self):
        claim = self.resolve("OffTest")
        self.assertEqual(claim.status, "SKIPPED")
        self.assertIn("7", claim.detail)

    def test_citation_of_a_test_that_no_longer_exists(self):
        claim = self.resolve("GoodTest.renamedAwayLongAgo")
        self.assertEqual(claim.status, "MISSING")

    def test_citation_of_a_class_that_does_not_exist(self):
        self.assertEqual(self.resolve("ImaginaryTest").status, "MISSING")

    def test_manual_claim(self):
        claim = self.resolve("L4:BRD-01")
        self.assertEqual(claim.status, "MANUAL")
        self.assertEqual(claim.level, "L4")

    def test_manual_claim_with_an_unknown_id(self):
        self.assertEqual(self.resolve("L4:NOPE-99").status, "MISSING")

    def test_requirement_takes_the_worst_of_its_claims(self):
        req = gen.Requirement("XX-01", "s", "text")
        req.claims = [gen.Claim("XX-01", "a", "", "PASS"), gen.Claim("XX-01", "b", "", "SKIPPED")]
        self.assertEqual(req.status, "SKIPPED")

    def test_requirement_with_no_claims_is_uncovered(self):
        self.assertEqual(gen.Requirement("XX-01", "s", "text").status, "UNCOVERED")


class CoverageMapTest(unittest.TestCase):
    def parse(self, text):
        original = gen.COVERAGE_MAP
        with tempfile.TemporaryDirectory() as tmp:
            gen.COVERAGE_MAP = write(Path(tmp), "coverage-map.tsv", text)
            try:
                return gen.parse_coverage_map()
            finally:
                gen.COVERAGE_MAP = original

    def test_reads_claims_and_ignores_comments(self):
        claims, errors = self.parse(
            "# a comment\n\nUI-01\tAlphaTest\tsome note\nUI-02\tBetaTest\n"
        )
        self.assertEqual(errors, [])
        self.assertEqual([(c.requirement, c.citation, c.note) for c in claims],
                         [("UI-01", "AlphaTest", "some note"), ("UI-02", "BetaTest", "")])

    def test_a_malformed_line_is_reported_not_dropped(self):
        claims, errors = self.parse("UI-01 AlphaTest\nUI-02\tBetaTest\n")
        self.assertEqual(len(claims), 1)
        self.assertEqual(len(errors), 1)
        self.assertIn("line 1", errors[0])


class RenderingTest(unittest.TestCase):
    def context(self, **overrides):
        req = gen.Requirement("UI-01", "Startup", "The splash screen appears")
        req.claims = [gen.Claim("UI-01", "SplashTest", "note", "PASS", "L1", "1 test(s) passed")]
        base = {
            "title": "T", "generated": "2020-01-01", "meta": [("Version", "1.0")],
            "levels": {k: {"declared": 0, "executed": 0, "passed": 0, "failed": 0,
                           "skipped": 0, "hidden": 0, "status": "-"} for k in gen.LEVELS},
            "totals": {"declared": 1, "executed": 1, "passed": 1, "failed": 0,
                       "skipped_entries": 0, "hidden": 0},
            "sections": {"Startup": [req]},
            "uncovered": [], "covered_reqs": 1, "total_reqs": 1, "coverage_pct": 100,
            "deferred": [], "v1_total": 1, "v1_covered": 1, "v1_pct": 100,
            "unbacked": [], "ignored_classes": [], "failures": [],
            "by_class": {"L1": {"SplashTest": [gen.TestCase("L1", "c.SplashTest", "a", "passed", 0.1)]}},
            "source_classes": {}, "map_errors": [], "l4": {"file.md": [("BRD-01", "Brake")]},
            "l4_total": 1,
        }
        base.update(overrides)
        return base

    def test_renders_a_self_contained_page(self):
        page = gen.render(self.context())
        self.assertTrue(page.startswith("<!DOCTYPE html>"))
        self.assertIn("</html>", page)
        # Self-contained means offline: no scripts, nothing fetched from a CDN.
        self.assertNotIn("<script", page.lower())
        self.assertNotIn("http://", page)
        self.assertNotIn("https://", page)

    def test_sections_run_evidence_before_interpretation(self):
        # A reader is shown the failures, then every test that ran, then the
        # manual checks -- and only then the claims made about them. Coverage
        # asserted before a single test name has appeared is coverage taken on
        # trust.
        page = gen.render(self.context())
        order = [
            "1. Results by test level",
            "2. How we test",
            "3. Failures",
            "4. Every automated test in this run",
            "5. Manual acceptance tests (L4)",
            "6. Requirements coverage",
            "7. Gaps and caveats",
        ]
        positions = [page.index(heading) for heading in order]
        self.assertEqual(positions, sorted(positions), "sections are out of order")

    def test_manual_tests_carry_a_human_instruction(self):
        page = gen.render(self.context())
        self.assertIn("Manual acceptance tests", page)
        self.assertIn("A human must execute them", page)

    def test_the_document_closes_on_the_human_checks(self):
        # L4 moved up the document, so the parting thought has to be restored
        # deliberately: the last thing a reader sees is that a person still has
        # to drive this.
        page = gen.render(self.context())
        self.assertIn("a human still has to run", page)
        self.assertGreater(page.index("a human still has to run"), page.index("7. Gaps and caveats"))

    def test_no_dangling_section_references(self):
        page = gen.render(self.context(
            deferred=[gen.Requirement("BE-01", "Backend API", "x", scope=gen.DEFERRED)],
            uncovered=[gen.Requirement("LD-02", "Lap detection", "y")],
        ))
        for reference in re.findall(r"see section (\d+)", page):
            self.assertIn(f"{reference}. ", page, f"section {reference} referenced but absent")

    def test_failures_are_shown_not_hidden(self):
        page = gen.render(self.context(
            failures=[gen.TestCase("L1", "c.BadTest", "breaks", "failed", 0.1, "assertion boom")],
            totals={"declared": 1, "executed": 1, "passed": 0, "failed": 1,
                    "skipped_entries": 0, "hidden": 0},
        ))
        self.assertIn("assertion boom", page)
        self.assertIn("1 test(s) failed", page)

    def test_hidden_tests_are_called_out_in_the_verdict(self):
        page = gen.render(self.context(
            totals={"declared": 10, "executed": 3, "passed": 3, "failed": 0,
                    "skipped_entries": 1, "hidden": 7},
            ignored_classes=[gen.SourceClass("L2", "OffTest", 7, ignored=True)],
        ))
        self.assertIn("7 declared test(s) never ran", page)
        self.assertIn("OffTest", page)

    def test_content_is_escaped(self):
        req = gen.Requirement("UI-01", "S", "text with <script>alert(1)</script>")
        page = gen.render(self.context(sections={"S": [req]}))
        self.assertNotIn("<script>alert", page)
        self.assertIn("&lt;script&gt;", page)


class DeterminismTest(unittest.TestCase):
    def test_same_inputs_produce_the_same_bytes(self):
        # A report that differs run to run cannot be diffed, and a report that
        # cannot be diffed cannot be reviewed.
        req = gen.Requirement("UI-01", "S", "text")
        req.claims = [
            gen.Claim("UI-01", "ZTest", "", "PASS", "L1", ""),
            gen.Claim("UI-01", "ATest", "", "PASS", "L1", ""),
        ]
        ctx = RenderingTest().context(sections={"S": [req]})
        first = gen.render(ctx)
        req.claims.reverse()
        self.assertEqual(first, gen.render(ctx))


class ScopeMapTest(unittest.TestCase):
    """Deferring a requirement must be visible, overridable, and impossible to abuse."""

    def requirements(self):
        return {
            "UM-01": gen.Requirement("UM-01", "User management", "Register"),
            "UM-18": gen.Requirement("UM-18", "User management", "Profile screen"),
            "LD-02": gen.Requirement("LD-02", "Lap detection", "Detect a lap"),
            "OC-01": gen.Requirement("OC-01", "Offline coaching", "Local insights"),
        }

    def apply(self, text, requirements=None):
        requirements = requirements or self.requirements()
        original = gen.SCOPE_MAP
        with tempfile.TemporaryDirectory() as tmp:
            gen.SCOPE_MAP = write(Path(tmp), "scope-map.tsv", text)
            try:
                errors = gen.parse_scope_map(requirements)
            finally:
                gen.SCOPE_MAP = original
        return requirements, errors

    def test_a_section_line_defers_every_requirement_in_it(self):
        reqs, errors = self.apply("V2-BACKEND\t@User management\tno server in V1\n")
        self.assertEqual(errors, [])
        self.assertTrue(reqs["UM-01"].deferred)
        self.assertTrue(reqs["UM-18"].deferred)
        self.assertFalse(reqs["LD-02"].deferred)
        self.assertEqual(reqs["UM-01"].scope_reason, "no server in V1")

    def test_a_requirement_line_overrides_its_section(self):
        # The exception stays on its own line rather than being buried in a sweep.
        reqs, errors = self.apply(
            "V2-BACKEND\t@User management\tno server\n"
            "V1\tUM-18\tProfile screen ships in V1\n"
        )
        self.assertEqual(errors, [])
        self.assertTrue(reqs["UM-01"].deferred)
        self.assertFalse(reqs["UM-18"].deferred)
        self.assertEqual(reqs["UM-18"].scope_reason, "Profile screen ships in V1")

    def test_override_order_in_the_file_does_not_matter(self):
        reqs, _ = self.apply(
            "V1\tUM-18\tships in V1\n"
            "V2-BACKEND\t@User management\tno server\n"
        )
        self.assertFalse(reqs["UM-18"].deferred)

    def test_deferring_something_that_has_a_passing_test_is_an_error(self):
        # The guard that stops this file becoming a place to hide inconvenient
        # requirements: it cannot be both unbuilt and proven.
        reqs = self.requirements()
        reqs["OC-01"].claims = [gen.Claim("OC-01", "OfflineCoachingEngineTest", "", "PASS")]
        _, errors = self.apply("V2-BACKEND\tOC-01\twrongly deferred\n", reqs)
        self.assertEqual(len(errors), 1)
        self.assertIn("OC-01", errors[0])
        self.assertIn("passing test", errors[0])

    def test_deferring_something_with_only_a_manual_claim_is_allowed(self):
        reqs = self.requirements()
        reqs["UM-01"].claims = [gen.Claim("UM-01", "L4:REG-01", "", "MANUAL")]
        _, errors = self.apply("V2-BACKEND\tUM-01\tno auth in V1\n", reqs)
        self.assertEqual(errors, [])

    def test_unknown_section_is_reported(self):
        _, errors = self.apply("V2-BACKEND\t@Nonexistent Section\ttypo\n")
        self.assertEqual(len(errors), 1)
        self.assertIn("Nonexistent Section", errors[0])

    def test_unknown_requirement_is_reported(self):
        _, errors = self.apply("V2-BACKEND\tZZ-99\ttypo\n")
        self.assertEqual(len(errors), 1)
        self.assertIn("ZZ-99", errors[0])

    def test_unknown_scope_value_is_reported(self):
        _, errors = self.apply("MAYBE\tLD-02\tvague\n")
        self.assertEqual(len(errors), 1)
        self.assertIn("MAYBE", errors[0])

    def test_a_deferred_requirement_with_no_claim_is_not_uncovered(self):
        reqs, _ = self.apply("V2-BACKEND\tUM-01\tno server\n")
        self.assertEqual(reqs["UM-01"].status, "V2-BACKEND")
        self.assertEqual(reqs["LD-02"].status, "UNCOVERED")

    def test_scope_defaults_to_v1(self):
        reqs, errors = self.apply("")
        self.assertEqual(errors, [])
        self.assertTrue(all(not r.deferred for r in reqs.values()))


class DeferredRenderingTest(unittest.TestCase):
    def page(self):
        v1 = gen.Requirement("LD-02", "Lap detection", "Detect a lap")
        deferred = gen.Requirement("BE-01", "Backend API", "Routes require a token")
        deferred.scope, deferred.scope_reason = gen.DEFERRED, "No server in V1"
        ctx = RenderingTest().context(
            sections={"Lap detection": [v1], "Backend API": [deferred]},
            uncovered=[v1], deferred=[deferred],
            v1_total=1, v1_covered=0, v1_pct=0,
            covered_reqs=0, total_reqs=2, coverage_pct=0,
        )
        return gen.render(ctx)

    def test_both_denominators_are_printed(self):
        # Either number alone misleads, so neither is allowed to appear on its own.
        page = self.page()
        self.assertIn("0 of 1 V1 requirements", page)
        self.assertIn("0 of 2", page)

    def test_deferred_requirements_are_listed_with_their_reason(self):
        page = self.page()
        self.assertIn("Deferred to V2", page)
        self.assertIn("BE-01", page)
        self.assertIn("No server in V1", page)

    def test_deferred_requirements_are_not_counted_as_uncovered(self):
        page = self.page()
        self.assertIn("1 of 1 V1 requirements carry no", page)

    def test_the_method_section_explains_the_scope_decision(self):
        self.assertIn("V1 is deliberately offline-first", self.page())


class BrandingTest(unittest.TestCase):
    """The helmet and the title.

    Cosmetic, but two of these guard real defects: incident #11 was this exact
    artwork rendered deformed, and an asset referenced rather than embedded
    would break silently only in the release directory, which is the one place
    a stranger ever opens the file.
    """

    LOGO_RE = re.compile(r'<img src="data:image/png;base64,[^"]+" width="(\d+)" height="(\d+)"')

    def context(self, **overrides):
        return RenderingTest().context(**overrides)

    def test_the_committed_asset_is_square(self):
        # Read the PNG header directly rather than through Pillow: the point is
        # that this asset needs no image library to be trustworthy.
        data = gen.LOGO.read_bytes()
        self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n", "not a PNG")
        width, height = struct.unpack(">II", data[16:24])
        self.assertEqual(width, height, f"helmet must be square, got {width}x{height}")

    def test_the_committed_asset_stays_small(self):
        # The 528x528 master is 257 KB, which is 343 KB base64 on a ~200 KB
        # report. If someone ever copies the master over this file, fail loudly
        # rather than quietly tripling every report we ship.
        size = gen.LOGO.stat().st_size
        self.assertLess(size, 16 * 1024, f"helmet grew to {size} bytes; re-scale it")

    def test_the_logo_is_embedded_not_linked(self):
        page = gen.render(self.context(logo=gen.logo_data_uri()))
        self.assertIn('<img src="data:image/png;base64,', page)
        # A relative src would survive the no-external-URL check and still be a
        # broken image once package-release.sh copies the HTML on its own.
        self.assertNotIn('<img src="05_tests', page)
        self.assertNotIn('<img src="helmet', page)

    def test_the_logo_is_scaled_uniformly(self):
        # Incident #11: the helmet rendered deformed by a non-uniform scale.
        page = gen.render(self.context(logo=gen.logo_data_uri()))
        match = self.LOGO_RE.search(page)
        self.assertIsNotNone(match, "logo img must state explicit width and height")
        self.assertEqual(match.group(1), match.group(2), "logo must be scaled square")

    def test_the_logo_precedes_the_title(self):
        page = gen.render(self.context(logo=gen.logo_data_uri()))
        self.assertLess(page.index("<img src=\"data:image/png"), page.index("<h1>"))

    def test_a_missing_logo_does_not_break_the_report(self):
        # An ornament must never be able to stop the evidence being produced.
        self.assertEqual(gen.logo_data_uri(Path("/nonexistent/helmet.png")), "")
        page = gen.render(self.context(logo=""))
        self.assertTrue(page.startswith("<!DOCTYPE html>"))
        self.assertNotIn("<img", page)

    def test_the_title_names_the_programme_and_the_product(self):
        self.assertEqual(gen.default_title("2.95"),
                         "Trillian · Driving Coach v2.95 — Test Report")

    def test_the_title_reaches_the_browser_tab_as_well_as_the_page(self):
        page = gen.render(self.context(title=gen.default_title("2.95")))
        self.assertIn("<title>Trillian · Driving Coach v2.95 — Test Report</title>", page)
        self.assertIn("<h1>Trillian · Driving Coach v2.95 — Test Report</h1>", page)


if __name__ == "__main__":
    unittest.main(verbosity=2)
