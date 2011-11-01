source ../testsupport.sh

rm -f test.xml test.csv

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

[ ! -f test.csv ] && err "Failed to find expected output test.csv"
[ ! -f test.xml ] && err "Failed to find expected output test.xml"

true
