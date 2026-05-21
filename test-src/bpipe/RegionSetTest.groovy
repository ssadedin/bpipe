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

    // ===== Partition Tests =====

    @Test
    void testPartition() {
        RegionSet rs = new RegionSet()
        rs.addSequence(new Sequence(name: 'chr1', range: new GenomicRange(0..1000)))

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
        rs.addSequence(new Sequence(name: 'chr1_random', range: new GenomicRange(0..300)))
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
