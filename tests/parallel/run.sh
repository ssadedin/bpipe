source ../testsupport.sh

rm -f test.txt.*

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"
grep -q "Stage world2" test.out || err "Failed to find expected stage world2"
grep -q "Stage end" test.out || err "Failed to find expected stage end"

[ ! -f test.txt.hello ] && err "Failed to find expected output test.txt.hello"
[ ! -f test.txt.hello.world ] && err "Failed to find expected output test.txt.hello.world"
[ ! -f test.txt.hello.world2 ] && err "Failed to find expected output test.txt.hello.world2"
[ ! -f test.txt.hello.world.end ] && err "Failed to find expected output test.txt.hello.world.end"

true
