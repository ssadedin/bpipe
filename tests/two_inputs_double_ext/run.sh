source ../testsupport.sh

run test.bam

grep -q file1 test_R1.fastq.world.txt || err "Failed to find word file1 in output file"

grep -q file2 test_R*.fastq.world.txt || err "Failed to find word file2 in output file"

true

