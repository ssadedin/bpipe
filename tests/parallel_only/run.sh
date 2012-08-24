source ../testsupport.sh

run

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"

true
