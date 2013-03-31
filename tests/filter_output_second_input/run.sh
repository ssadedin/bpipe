source ../testsupport.sh

run test.vcf test.bam

exists test.extract.bam

true
