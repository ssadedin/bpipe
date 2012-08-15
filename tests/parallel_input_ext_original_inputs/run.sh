source ../testsupport.sh

source cleanup.sh

run *.chr*.*vcf *.chr*bam

grep -c chr14 test.out | grep -q 1 || err "Unexpected number of references to chr14 - should be only 1"
grep -c chr15 test.out | grep -q 1 || err "Unexpected number of references to chr15 - should be only 1"


true
