source ../testsupport.sh

. ./clean.sh

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

[ ! -f test.f1 ] && err "Failed to find expected output test.f1"
[ ! -f test.f2 ] && err "Failed to find expected output test.f2"
[ ! -f test.f3 ] && err "Failed to find expected output test.f3"

true
