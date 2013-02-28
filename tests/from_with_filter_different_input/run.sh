source ../testsupport.sh

run test.bam test.vcf

exists test.clean.vcf

true
