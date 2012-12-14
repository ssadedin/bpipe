source ../testsupport.sh

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

[ ! -f mars.txt ] && err "Failed to find expected output mars.txt"
[ ! -f world.txt ] && err "Failed to find expected output world.txt"

bpipe query > query.out

grep -q "world.txt" query.out || err "Failed to find world.txt in dependency graph"
grep -q "mars.txt" query.out || err "Failed to find world.txt in dependency graph"

true
