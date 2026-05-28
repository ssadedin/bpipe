package bpipe

import org.junit.Before
import org.junit.Test
import static org.junit.Assert.*

class RegionSetTest {

    File tempDir

    @Before
    void setUp() {
        tempDir = File.createTempDir("regionset_test", "")
        tempDir.deleteOnExit()
    }

    // ===== Construction Tests =====

    @Test
    void testConstructFromSingleSequence() {
        Sequence seq = new Sequence(name: 'chr1', range: new GenomicRange(0..1000))
        RegionSet rs = new RegionSet(seq)

        assertEquals(1, rs.sequences.size())
        assertTrue(rs.sequences.values().any { it.name == 'chr1' })
    }

    @Test
    void testConstructFromSequenceList() {
        Sequence seq1 = new Sequence(name: 'chr1', range: new GenomicRange(0..1000))
        Sequence seq2 = new Sequence(name: 'chr2', range: new GenomicRange(0..2000))
        RegionSet rs = new RegionSet([seq1, seq2])

        assertEquals(2, rs.sequences.size())
    }

    @Test
    void testChromosomeNamesUpdatedOnConstruction() {
        Sequence seq1 = new Sequence(name: 'chr1', range: new GenomicRange(0..1000))
        Sequence seq2 = new Sequence(name: 'chr2', range: new GenomicRange(0..2000))
        RegionSet rs = new RegionSet([seq1, seq2])

        assertTrue(rs.chromosomeNames.contains('chr1'))
        assertTrue(rs.chromosomeNames.contains('chr2'))
        assertEquals(2, rs.chromosomeNames.size())
    }

    @Test
    void testAddSequenceUpdatesChromosomeNames() {
        RegionSet rs = new RegionSet()
        Sequence seq = new Sequence(name: 'chr3', range: new GenomicRange(0..500))
        rs.addSequence(seq)

        assertTrue(rs.chromosomeNames.contains('chr3'))
        assertEquals(1, rs.chromosomeNames.size())
    }

    @Test
    void testRemoveSequenceUpdatesChromosomeNames() {
        Sequence seq1 = new Sequence(name: 'chr1', range: new GenomicRange(0..1000))
        Sequence seq2 = new Sequence(name: 'chr2', range: new GenomicRange(0..2000))
        RegionSet rs = new RegionSet([seq1, seq2])

        rs.removeSequence(seq1)

        assertFalse(rs.chromosomeNames.contains('chr1'))
        assertTrue(rs.chromosomeNames.contains('chr2'))
        assertEquals(1, rs.chromosomeNames.size())
    }

    @Test
    void testRemoveSequencePreservesSharedChromosomeName() {
        Sequence seq1 = new Sequence(name: 'chr1', range: new GenomicRange(0..1000))
        Sequence seq2 = new Sequence(name: 'chr1', range: new GenomicRange(2000..3000))
        RegionSet rs = new RegionSet([seq1, seq2])

        rs.removeSequence(seq1)

        // chr1 should still be present because seq2 has the same name
        assertTrue(rs.chromosomeNames.contains('chr1'))
        assertEquals(1, rs.chromosomeNames.size())
    }

    @Test(expected = IllegalArgumentException)
    void testRemoveSequenceNotPresent() {
        Sequence seq1 = new Sequence(name: 'chr1', range: new GenomicRange(0..1000))
        Sequence seq2 = new Sequence(name: 'chr2', range: new GenomicRange(0..2000))
        RegionSet rs = new RegionSet([seq1])

        rs.removeSequence(seq2)
    }

    // ===== Size Tests =====

    @Test
    void testSize() {
        Sequence seq1 = new Sequence(name: 'chr1', range: new GenomicRange(0..1000))
        Sequence seq2 = new Sequence(name: 'chr2', range: new GenomicRange(0..2000))
        RegionSet rs = new RegionSet([seq1, seq2])

        assertEquals(3000L, rs.size())
    }

    @Test
    void testEmptyRegionSetSize() {
        RegionSet rs = new RegionSet()
        assertEquals(0L, rs.size())
    }

    // ===== BED File Tests =====

    @Test
    void testBedFileLoading() {
        File bedFile = new File(tempDir, "test.bed")
        bedFile.text = [
            "chr1\t100\t500",
            "chr2\t200\t800",
            "chr3\t0\t1000"
        ].join("\n")

        RegionSet rs = RegionSet.bed(bedFile.absolutePath)

        assertEquals(3, rs.sequences.size())
        assertTrue(rs.chromosomeNames.contains('chr1'))
        assertTrue(rs.chromosomeNames.contains('chr2'))
        assertTrue(rs.chromosomeNames.contains('chr3'))
    }

    @Test
    void testBedFileWithPadding() {
        File bedFile = new File(tempDir, "padded.bed")
        bedFile.text = "chr1\t100\t500\n"

        RegionSet rs = RegionSet.bed([padding: 50], bedFile.absolutePath)

        Sequence seq = rs.sequences.values().first()
        assertEquals(50, seq.range.from)
        assertEquals(550, seq.range.to)
    }

    @Test
    void testBedFileWithPaddingDoesNotGoBelowZero() {
        File bedFile = new File(tempDir, "padded_zero.bed")
        bedFile.text = "chr1\t10\t500\n"

        RegionSet rs = RegionSet.bed([padding: 50], bedFile.absolutePath)

        Sequence seq = rs.sequences.values().first()
        assertEquals(0, seq.range.from)
    }

    @Test(expected = PipelineError)
    void testBedFileNotFound() {
        RegionSet.bed("/nonexistent/path/file.bed")
    }

    @Test(expected = PipelineError)
    void testBedFileNull() {
        RegionSet.bed((String) null)
    }

    @Test
    void testBedFileMultipleRegionsSameChromosome() {
        File bedFile = new File(tempDir, "multi_chr.bed")
        bedFile.text = [
            "chr1\t100\t500",
            "chr1\t1000\t2000",
            "chr2\t0\t300"
        ].join("\n")

        RegionSet rs = RegionSet.bed(bedFile.absolutePath)

        assertEquals(3, rs.sequences.size())
        assertTrue(rs.chromosomeNames.contains('chr1'))
        assertTrue(rs.chromosomeNames.contains('chr2'))
        assertEquals(2, rs.chromosomeNames.size())
    }

    @Test
    void testBedFileFromFileObject() {
        File bedFile = new File(tempDir, "file_obj.bed")
        bedFile.text = "chr1\t0\t1000\n"

        RegionSet rs = RegionSet.bed(bedFile)

        assertEquals(1, rs.sequences.size())
    }

    // ===== Split/Group Tests (without byChromosome) =====

    @Test
    void testGroupIntoFewerParts() {
        // Create a region set with 4 chromosomes of equal size
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..1000)))
        rs.addSequence(new Sequence(name: 'chr2', range: new GenomicRange(0..1000)))
        rs.addSequence(new Sequence(name: 'chr3', range: new GenomicRange(0..1000)))
        rs.addSequence(new Sequence(name: 'chr4', range: new GenomicRange(0..1000)))

        Set<RegionSet> result = rs.group(2)

        assertEquals(2, result.size())
    }

    @Test
    void testGroupIntoMoreParts() {
        // Create a region set with 1 large chromosome with genes
        Sequence seq = new Sequence(name: 'chr1', range: new GenomicRange(0..10000))
        seq.add("gene1", 100, 200)
        seq.add("gene2", 5000, 5100)
        seq.add("gene3", 9000, 9100)

        RegionSet rs = new RegionSet()
        rs.addSequence(seq)

        Set<RegionSet> result = rs.group(2)

        assertEquals(2, result.size())
        // Both parts should have non-zero size
        result.each { assertTrue(it.size() > 0) }
    }

    @Test
    void testGroupSingleSequenceNoSplit() {
        // With allowBreaks: false, a single sequence cannot be split
        Sequence seq = new Sequence(name: 'chr1', range: new GenomicRange(0..10000))
        RegionSet rs = new RegionSet()
        rs.addSequence(seq)

        Set<RegionSet> result = rs.group([allowBreaks: false], 3)

        // Cannot split a single sequence without breaks, so should remain 1
        assertEquals(1, result.size())
    }

    @Test
    void testGroupPreservesAllSequences() {
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..1000)))
        rs.addSequence(new Sequence(name: 'chr2', range: new GenomicRange(0..2000)))
        rs.addSequence(new Sequence(name: 'chr3', range: new GenomicRange(0..3000)))

        Set<RegionSet> result = rs.group(2)

        // Total size should be preserved
        long totalSize = result.sum { it.size() } as long
        assertEquals(rs.size(), totalSize)
    }

    @Test
    void testSplitSynonymForGroup() {
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..1000)))
        rs.addSequence(new Sequence(name: 'chr2', range: new GenomicRange(0..2000)))
        rs.addSequence(new Sequence(name: 'chr3', range: new GenomicRange(0..3000)))
        rs.addSequence(new Sequence(name: 'chr4', range: new GenomicRange(0..4000)))

        Set<RegionSet> result = rs.split(2)

        assertEquals(2, result.size())
    }

    // ===== Split/Group Tests (with byChromosome) =====

    @Test
    void testGroupByChromosomeOnlyCombinesSameChromosome() {
        // Create regions from different chromosomes
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..1000)))
        rs.addSequence(new Sequence(name: 'chr2', range: new GenomicRange(0..1000)))
        rs.addSequence(new Sequence(name: 'chr3', range: new GenomicRange(0..1000)))

        // Request 2 parts with byChromosome - since all are different chromosomes,
        // no combining should be possible
        Set<RegionSet> result = rs.group([byChromosome: true], 2)

        // Should remain 3 parts since no two share a chromosome
        assertEquals(3, result.size())
    }

    @Test
    void testGroupByChromosomeCombinesSameChromosome() {
        // Create multiple regions from the same chromosome
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..1000)))
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(2000..3000)))
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(5000..6000)))
        rs.addSequence(new Sequence(name: 'chr2', range: new GenomicRange(0..1000)))

        // Request 2 parts with byChromosome
        Set<RegionSet> result = rs.group([byChromosome: true], 2)

        // chr1 regions can be combined together, chr2 stays separate
        // Result should be 2 parts
        assertEquals(2, result.size())

        // Verify no region set contains sequences from different chromosomes
        result.each { RegionSet regionSet ->
            assertEquals("Region set should only contain one chromosome",
                1, regionSet.chromosomeNames.size())
        }
    }

    @Test
    void testGroupByChromosomeWithMixedChromosomes() {
        // 3 regions from chr1, 2 from chr2, 1 from chr3
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..1000)))
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(2000..3000)))
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(5000..6000)))
        rs.addSequence(new Sequence(name: 'chr2', range: new GenomicRange(0..1000)))
        rs.addSequence(new Sequence(name: 'chr2', range: new GenomicRange(3000..4000)))
        rs.addSequence(new Sequence(name: 'chr3', range: new GenomicRange(0..1000)))

        // Request 3 parts with byChromosome
        Set<RegionSet> result = rs.group([byChromosome: true], 3)

        // Should produce 3 parts: one per chromosome
        assertEquals(3, result.size())

        // Verify each region set only contains one chromosome
        result.each { RegionSet regionSet ->
            assertEquals("Region set should only contain one chromosome",
                1, regionSet.chromosomeNames.size())
        }
    }

    @Test
    void testGroupByChromosomeCannotReduceBelowChromosomeCount() {
        // 3 different chromosomes, request 1 part
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..1000)))
        rs.addSequence(new Sequence(name: 'chr2', range: new GenomicRange(0..2000)))
        rs.addSequence(new Sequence(name: 'chr3', range: new GenomicRange(0..3000)))

        Set<RegionSet> result = rs.group([byChromosome: true], 1)

        // Cannot combine different chromosomes, so minimum is 3
        assertEquals(3, result.size())
    }

    @Test
    void testGroupWithoutByChromosomeCombinesDifferentChromosomes() {
        // 3 different chromosomes, request 1 part - without byChromosome they should combine
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..1000)))
        rs.addSequence(new Sequence(name: 'chr2', range: new GenomicRange(0..2000)))
        rs.addSequence(new Sequence(name: 'chr3', range: new GenomicRange(0..3000)))

        Set<RegionSet> result = rs.group(1)

        // Without byChromosome, all should be combined into 1
        assertEquals(1, result.size())
    }

    @Test
    void testGroupByChromosomeWithAllowBreaksFalse() {
        // Multiple regions from same chromosome, disallow breaks
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..5000)))
        rs.addSequence(new Sequence(name: 'chr2', range: new GenomicRange(0..5000)))

        Set<RegionSet> result = rs.group([byChromosome: true, allowBreaks: false], 4)

        // Can't split sequences and can't combine across chromosomes
        // Should remain at 2 parts
        assertEquals(2, result.size())
    }

    // ===== Rebalancing Tests =====

    @Test
    void testRebalancingSplitsMultipleLargeRegionsOfSameSize() {
        // Simulate the scenario: two large regions (~1Mb) and many small ones
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr8', range: new GenomicRange(0..1000000)))
        rs.addSequence(new Sequence(name: 'chr6', range: new GenomicRange(0..1000000)))
        // Add several small regions
        (1..10).each { i ->
            rs.addSequence(new Sequence(name: "chr${10+i}".toString(), range: new GenomicRange(0..100000)))
        }

        Set<RegionSet> result = rs.group(12)

        assert result.size() == 12

        // The largest region should have been split, so no single part should be close to 1Mb
        long maxSize = result.max { it.size() }.size()
        long minSize = result.min { it.size() }.size()

        // The largest should be no more than 4x the smallest after rebalancing
        assert maxSize < 4 * minSize : "Rebalancing failed: max=${maxSize}, min=${minSize}, ratio=${maxSize/minSize}"
    }

    // ===== Partition Tests =====

    @Test
    void testPartition() {
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..999)))

        Set<RegionSet> result = rs.partition(500)

        // 1000 / 500 = 2 partitions
        assertEquals(2, result.size())
    }

    // ===== Minor Contig Tests =====

    @Test
    void testRemoveMinorContigs() {
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..1000)))
        rs.addSequence(new Sequence(name: 'chrUn_gl000220', range: new GenomicRange(0..500)))
        rs.addSequence(new Sequence(name: 'chrM', range: new GenomicRange(0..200)))

        rs.removeMinorContigs()

        assertEquals(1, rs.sequences.size())
        assertTrue(rs.sequences.values().any { it.name == 'chr1' })
    }

    @Test
    void testIsMinorContig() {
        RegionSet rs = new RegionSet()

        assertTrue(rs.isMinorContig('NC_007605'))
        assertTrue(rs.isMinorContig('GL000220'))
        assertTrue(rs.isMinorContig('Un_gl000220'))
        assertTrue(rs.isMinorContig('chrUn_gl000220'))
        assertTrue(rs.isMinorContig('chrM'))
        assertTrue(rs.isMinorContig('chr1_random'))
        assertTrue(rs.isMinorContig('chr6_cox_hap2'))
        assertTrue(rs.isMinorContig('chr1_alt'))
        assertTrue(rs.isMinorContig('chr1_fix'))
        assertTrue(rs.isMinorContig('chrEBV'))
        assertTrue(rs.isMinorContig('HLA-A'))

        assertFalse(rs.isMinorContig('chr1'))
        assertFalse(rs.isMinorContig('chr22'))
        assertFalse(rs.isMinorContig('chrX'))
        assertFalse(rs.isMinorContig('chrY'))
    }

    // ===== Overlaps Tests =====

    @Test
    void testOverlapsWithChrAndRange() {
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(100..500)))

        assertTrue(rs.overlaps('chr1', 200, 300))
        assertTrue(rs.overlaps('chr1', 50, 150))
        assertFalse(rs.overlaps('chr1', 600, 700))
        assertFalse(rs.overlaps('chr2', 200, 300))
    }

    @Test
    void testOverlapsWithChrOnly() {
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..1000)))

        assertTrue(rs.overlaps('chr1'))
        assertFalse(rs.overlaps('chr2'))
    }

    // ===== Sequential Split Tests =====

    @Test
    void testSequentialSplitGroupsAdjacentRegions() {
        // 4 equal regions on chr1, split into 2
        // target = 1000, expect first two regions in part 1, last two in part 2
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..300)))
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(400..700)))
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(800..1000)))
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(1100..1300)))

        List<RegionSet> result = rs.splitSequential(2)

        assert result.size() == 2
        // All sequences must remain on chr1
        result.each { assert it.chromosomeNames == ['chr1'] as Set }
        // Total size preserved
        assert result.sum { it.size() } as long == rs.size()
    }

    @Test
    void testSequentialSplitChromosomeBoundaryForcesNewPart() {
        // chr1 and chr2 must never be in the same part
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..500)))
        rs.addSequence(new Sequence(name: 'chr2', range: new GenomicRange(0..500)))

        // Even if we request 1 part, chromosome boundary forces 2
        List<RegionSet> result = rs.splitSequential(1)

        assert result.size() == 2
        assert result.every { it.chromosomeNames.size() == 1 }
    }

    @Test
    void testSequentialSplitMultipleChromosomesEachSeparate() {
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..1000)))
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(2000..3000)))
        rs.addSequence(new Sequence(name: 'chr2', range: new GenomicRange(0..1000)))
        rs.addSequence(new Sequence(name: 'chr2', range: new GenomicRange(2000..3000)))

        List<RegionSet> result = rs.splitSequential(2)

        // Exactly 2 parts (one per chromosome)
        assert result.size() == 2
        result.each { assert it.chromosomeNames.size() == 1 }
        Set<String> chrs = result.collectMany { it.chromosomeNames.toList() } as Set
        assert chrs == ['chr1', 'chr2'] as Set
    }

    @Test
    void testSequentialSplitRegionSplitAtBoundary() {
        // One accumulation region (0-800) then a large region (800-2000).
        // target = 1000; when we reach 800 (≥750) and the large region would push to 2000 (>1250),
        // the large region should be split at position 800+200=1000.
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..800)))
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(800..2000)))

        List<RegionSet> result = rs.splitSequential(2)

        assert result.size() == 2
        // Total size preserved
        assert result.sum { it.size() } as long == rs.size()
        // First part should contain 1000 bp (0..800 + 800..1000)
        assert result[0].size() == 1000L
    }

    @Test
    void testSequentialSplitMaxDistanceForcesNewPart() {
        // Two chr1 regions far apart; maxDistance=1000 should force a split between them
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..100)))
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(50000..50100)))

        List<RegionSet> result = rs.splitSequential([maxDistance: 1000], 1)

        assert result.size() == 2
        assert result[0].sequences.size() == 1
        assert result[1].sequences.size() == 1
    }

    @Test
    void testSequentialSplitMaxDistanceNoSplitWhenClose() {
        // Two chr1 regions close together; gap < maxDistance, so they stay in one part
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..100)))
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(200..300)))

        List<RegionSet> result = rs.splitSequential([maxDistance: 1000], 1)

        assert result.size() == 1
        assert result[0].sequences.size() == 2
    }

    @Test
    void testSequentialSplitMaxDistanceAndChromosomeBothTrigger() {
        // chr1: two close regions then a big gap; chr2: one region
        // maxDistance=1000 should keep the close pair together, split from the distant one,
        // and chromosome boundary always separates chr2
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..100)))
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(200..300)))
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(100000..100100)))
        rs.addSequence(new Sequence(name: 'chr2', range: new GenomicRange(0..100)))

        List<RegionSet> result = rs.splitSequential([maxDistance: 1000], 2)

        // chr1-close, chr1-distant, chr2 → 3 parts
        assert result.size() == 3
        result.each { assert it.chromosomeNames.size() == 1 }
    }

    @Test
    void testSequentialSplitViaOptions() {
        // Verify that split(sequential:true, N) delegates to splitSequential
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..500)))
        rs.addSequence(new Sequence(name: 'chr2', range: new GenomicRange(0..500)))

        Set<RegionSet> result = rs.split(sequential: true, 1)

        assert result.size() == 2
        result.each { assert it.chromosomeNames.size() == 1 }
    }

    @Test
    void testSequentialSplitEmptyRegionSet() {
        RegionSet rs = new RegionSet()
        List<RegionSet> result = rs.splitSequential(4)
        assert result.isEmpty()
    }

    // ===== Region Value Tests =====

    @Test
    void testGetRegion() {
        Sequence seq = new Sequence(name: 'chr1', range: new GenomicRange(100..500))
        RegionSet rs = new RegionSet([seq])

        RegionValue rv = rs.getRegion()
        assertNotNull(rv)
        assertTrue(rv.toString().contains('chr1'))
    }
}
