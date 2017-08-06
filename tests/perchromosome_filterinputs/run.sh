source ../testsupport.sh

run *.bam

[ `grep -i -c "Using bam file test.chr1.bam" test.out` == 2 ] || err "Incorrect number of references to test.chr1.bam"

true

