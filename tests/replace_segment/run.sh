source ../testsupport.sh

run

grep -q "Stage stupid .jupiter." test.out || err "Failed to find output from stage replaced by branch"

grep -q "^stupid" test.out || err "Failed to find expected word from stage replaced by branch"
grep -q "^jupiter" test.out || err "Failed to find expected word from stage replaced by branch"

true

