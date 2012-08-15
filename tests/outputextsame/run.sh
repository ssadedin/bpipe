source ../testsupport.sh

run *.chr14.*vcf *.chr14.*bam

grep -q "MUW09_C0R8HACXX_ATCACG_L001_R1.fastq.merge.dedupe.merge.reorder.rg.recal.chr14.realign.bam => MUW09_C0R8HACXX_ATCACG_L001_R1.fastq.merge.dedupe.merge.reorder.rg.recal.chr14.realign.test.bam"  test.out  || err "Expected MUW09_C0R8HACXX_ATCACG_L001_R1.fastq.merge.dedupe.merge.reorder.rg.recal.chr14.realign.bam => MUW09_C0R8HACXX_ATCACG_L001_R1.fastq.merge.dedupe.merge.reorder.rg.recal.chr14.realign.test.bam in output"


true
