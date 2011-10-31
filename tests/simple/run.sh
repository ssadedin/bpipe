source ../testsupport.sh

rm -f test.world.txt test.txt

run

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

[ ! -f test.txt ] && err "Failed to find expected output test.txt"
[ ! -f test.world.txt ] && err "Failed to find expected output test.world.txt"

true
