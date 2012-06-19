source ../testsupport.sh

. ./cleanup.sh

run

grep -q "Stage there" test.out || err "Failed to find expected stage there"
grep -q "Stage world" test.out || err "Failed to find expected stage world"
grep -q "Stage buddy" test.out || err "Failed to find expected stage buddy"

grep -q "Stage foo" test.out && err "Observed segment stage foo printed as actual stage"
grep -q "Stage [0-9]" test.out && err "Observed segment stage joiner printed as actual stage"

[ ! -f test.world.txt ] && err "Failed to find expected output test.world.txt"

true
