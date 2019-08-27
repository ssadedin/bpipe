source ../testsupport.sh

bpipe test test.groovy  FOO_R1.fastq.gz  FOO_R2.fastq.gz  > test.out 2>&1

grep -q 'FOO.Aligned.out.bam' test.out || err 'Transform using regex not applied or substituted correctly'

true

