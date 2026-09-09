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

    def test_manual_tests_come_last_with_a_human_instruction(self):
        page = gen.render(self.context())
        self.assertIn("Manual acceptance tests", page)
        self.assertIn("A human must execute them", page)
        self.assertGreater(page.index("Manual acceptance tests"), page.index("Requirements coverage"))

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


if __name__ == "__main__":
    unittest.main(verbosity=2)
