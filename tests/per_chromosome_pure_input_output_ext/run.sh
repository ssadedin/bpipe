source ../testsupport.sh

run test.bam

exists test.chr1.hello.bam
exists test.chr2.hello.bam

true
