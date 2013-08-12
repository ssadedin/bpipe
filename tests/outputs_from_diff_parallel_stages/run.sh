source ../testsupport.sh

run test.bed test.bam

grep Stage test.out | head -2 | grep -q align && err "Found align stage too early"

true
