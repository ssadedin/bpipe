source ../testsupport.sh

run test.txt

grep -q 'test.hello.there.vep.csv' test.out && err "Output contains reference to incorrect file, test.hello.there.vep.csv"

exists variants/test.hello.vep.vcf

true
