source ../testsupport.sh

rm -f test.foo.txt test.foo.bar.txt

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

[ ! -f test.foo.txt ] && err "Failed to find expected output test.foo.txt"
[ ! -f test.foo.bar.txt ] && err "Failed to find expected output test.foo.bar.txt"

true
