source ../testsupport.sh

. cleanup.sh

run test.txt

grep -q "Stage folder" test.out || err "Failed to find expected stage hello"
grep -q "Stage after" test.out || err "Failed to find expected stage world"

grep -q "Skipping" test.out && err "Skipped steps incorrectly on first run"

[ ! -f thefolder/output.csv ] && err "Failed to find expected output test.txt"
[ ! -f output.after.xml ] && err "Failed to find expected output test.world.txt"

bpipe query > test.out

grep -q "test.txt =>" test.out || err "Failed to find input test.txt in dependency graph"
grep -q "output.csv =>" test.out || err "Failed to find output.csv in dependency graph"
grep -q "output.after.xml" test.out || err "Failed to find output.after.xml in dependency graph"

run test.txt

grep -q "Skipping steps to create.*newer than test.txt" test.out || err "Failed to skip folder creation step on second run"
grep -q "Skipping command .*output.after.xml" test.out || err "Failed to skip stage after folder stage on second run"

true
