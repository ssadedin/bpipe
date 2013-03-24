source ../testsupport.sh

run test.fastq

exists test.align.bai
exists test.align.bam
exists test.align.filter_bam.bam

bpipe cleanup -y >> test.out

exists test.align.filter_bam.bam

# Should be cleaned up because it is an internal node
notexists test.align.bam

# Should be cleaned up because it accompanies an internal node
notexists test.align.bai

true
