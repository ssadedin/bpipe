source ../testsupport.sh

rm -f test.txt.*

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

[ ! -f test.txt.hello ] && err "Failed to find expected output test.txt"
[ ! -f test.txt.hello.world ] && err "Failed to find expected output test.world.txt"

true
