source ../testsupport.sh

./cleanup.sh

run

grep -q "Stage init" test.out || err "Failed to find expected stage init"
grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage there" test.out || err "Failed to find expected stage there"
grep -q "Stage world" test.out || err "Failed to find expected stage world"
grep -q "Stage fail" test.out || err "Failed to find expected stage fail"

grep -q "Command failed with exit status" test.out || err "Pipeline should have failed in last stage but didn't."

true
