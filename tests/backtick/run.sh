source ../testsupport.sh

run

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "world" test.out || err "Failed to find expected output world"

true
