source ../testsupport.sh

run

grep -q "Stage child_stage" test.out || err "Failed to find expected stage child_stage"

true
