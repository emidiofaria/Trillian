package com.drivingcoach.data.track

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the requirements document and Annex A to each other, and both to the shipped
 * catalogue.
 *
 * Earlier drafts of the SRS justified the track-library requirements by naming the circuits
 * they were derived from, with each circuit's headings, distances and margins written into the
 * prose. That reads well at two circuits and does not survive twenty: it makes rules and
 * examples indistinguishable, and it means adding a circuit requires editing the specification.
 * TL-17 forbids it and TL-18 requires that every shipped circuit appear in Annex A instead.
 *
 * Of the two, TL-18 is the one with teeth. A register nobody is forced to update goes stale
 * silently, and stale evidence is worse than none because it is still believed. The TL-17 check
 * is by comparison a regression guard on a state that is already correct — it passes today, and
 * its job is to keep passing.
 *
 * Both documents are read by path, for the same reason the catalogue tests read the asset by
 * path: the point is to assert against the bytes that are actually committed rather than a copy
 * that could drift. Gradle cannot infer that dependency, so `01_requirements` is declared as an
 * explicit test input in `app/build.gradle.kts` — without it, editing the SRS leaves
 * `testDebugUnitTest` UP-TO-DATE and this class silently does not run
 * (`FP-UNDECLARED-TEST-INPUT`).
 */
class CircuitEvidenceAnnexTest {

    private val catalogueFile = File("src/main/assets/tracks/tracks.json")
    private val srsFile = File("../01_requirements/DrivingCoach_SRS_v1.md")
    private val annexFile = File("../01_requirements/ANNEX_A_circuit_evidence.md")

    private fun catalogue(): List<Track> = BundledTrackCatalog.parse(catalogueFile.readText())

    /**
     * Circuit identifiers declared by Annex A, taken from the first column of its tables.
     *
     * Reading the first column rather than searching the whole file matters: the annex is full
     * of backticked tokens that are not circuits (`MAP_COORDINATES`, `fastestLapMs`,
     * `LocalLapDetector`), and a whole-file search would accept a circuit mentioned in passing
     * as though it had a row.
     */
    private fun annexDeclaredIds(): Set<String> =
        annexFile.readLines()
            .filter { it.trimStart().startsWith("|") }
            .mapNotNull { row ->
                row.trim().removePrefix("|").split("|").firstOrNull()
                    ?.trim()
                    ?.removeSurrounding("`")
                    ?.takeIf { cell -> cell.isNotEmpty() && cell.all { it.isLowerCase() || it.isDigit() || it == '_' } }
            }
            .toSet()

    @Test
    fun bothRequirementsDocumentsArePresent() {
        assertTrue("the SRS is missing: ${srsFile.absolutePath}", srsFile.exists())
        assertTrue(
            "Annex A is missing: ${annexFile.absolutePath}. TL-18 requires it to exist " +
                "alongside the SRS; a catalogue without its evidence register cannot ship.",
            annexFile.exists()
        )
    }

    @Test
    fun noRequirementNamesASpecificCircuit() {
        val tracks = catalogue()
        val forbidden = tracks.flatMap { listOf(it.id, it.name) }.filter { it.isNotBlank() }

        // A requirement row, as opposed to explanatory prose: "| TL-04 | The app shall ..."
        val requirementRow = Regex("""^\|\s*[A-Z]{2,}-\d+\s*\|""")

        val offences = srsFile.readLines()
            .withIndex()
            .filter { (_, line) -> requirementRow.containsMatchIn(line) }
            .flatMap { (index, line) ->
                forbidden
                    .filter { line.contains(it, ignoreCase = true) }
                    .map { "line ${index + 1} names \"$it\"" }
            }

        assertTrue(
            "TL-17: requirements state rules that hold for every circuit, so no requirement " +
                "may name one. Move the circuit-specific part to Annex A and leave the " +
                "requirement general. Offending rows: ${offences.joinToString("; ")}",
            offences.isEmpty()
        )
    }

    @Test
    fun everyShippedCircuitHasAnAnnexEntry() {
        val declared = annexDeclaredIds()
        val missing = catalogue().map { it.id }.filterNot { declared.contains(it) }

        assertTrue(
            "TL-18: these circuits ship in tracks.json but have no row in Annex A: " +
                "${missing.joinToString(", ")}. A circuit is a set of claims about a real " +
                "place; shipping one without recording what evidence supports it, and what " +
                "tier that evidence earns, leaves the release channel undecidable " +
                "(see docs/RELEASE.md). Add a row to §A.1 and §A.2.",
            missing.isEmpty()
        )
    }

    @Test
    fun annexDescribesNoCircuitThatIsNotShipped() {
        val shipped = catalogue().map { it.id }.toSet()
        val orphans = annexDeclaredIds().filterNot { shipped.contains(it) }

        assertTrue(
            "TL-18: Annex A has rows for circuits that are not in tracks.json: " +
                "${orphans.joinToString(", ")}. Either the circuit was removed from the " +
                "catalogue and its evidence left behind, or the identifier is misspelled. " +
                "Evidence for a circuit nobody can select is evidence that will be trusted " +
                "and is not being checked.",
            orphans.isEmpty()
        )
    }
}
