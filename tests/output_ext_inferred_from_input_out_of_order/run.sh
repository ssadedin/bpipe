source ../testsupport.sh

run test.bam test.vcf

run test.bam test.vcf

grep -q 'could not be found' test.out && err "Unexpected error occurred: expected output could not be found"

true

