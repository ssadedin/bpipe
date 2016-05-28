source ../testsupport.sh

run test_R1.fastq.gz

grep -q "ERROR:" test.out && err "Error reported in output"

exists test_R1.clean.fastq.gz


