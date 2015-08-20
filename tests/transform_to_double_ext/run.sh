source ../testsupport.sh

run test.bam

exists test.median.txt 

rm -f *.median.txt

bpipe run test_fail.groovy test.bam > test2.out

notexists test.median.txt 

grep -q "ERROR: stage hello failed: An output containing or ending with '.foo' was referenced"  test2.out || err "Did not see expected error message"

true



