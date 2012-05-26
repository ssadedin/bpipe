source ../testsupport.sh

. ./clean.sh

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

[ ! -f test.f1.txt ] && err "Failed to find expected output test.f1.txt"
[ ! -f test.f1.f2.txt ] && err "Failed to find expected output test.f1.f2.txt"
[ ! -f test.f1.f3.txt ] && err "Failed to find expected output test.f2.f3.txt"

true
