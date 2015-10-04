source ../testsupport.sh

run test.bam

exists foo/test.hello.rpkm

grep -q ERROR test.out && err "Error reported in test log for good script"

true
