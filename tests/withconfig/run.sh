source ../testsupport.sh

./cleanup.sh

run

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Command failed with exit status" test.out || err "Failed to find expected stage hello"

true
