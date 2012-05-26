source ../testsupport.sh

rm -f test.*vcf

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage filter_with_ext" test.out || err "Failed to find expected stage filter_with_ext"

[ ! -f test.vcf ] && err "Failed to find expected output test.vcf"
[ ! -f test.fix.vcf ] && err "Failed to find expected output test.fix.vcf"

true
