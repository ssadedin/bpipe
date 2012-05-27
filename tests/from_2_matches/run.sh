source ../testsupport.sh

source ./cleanup.sh

run test.txt test2.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

[ ! -f test.hello.txt ] && err "Failed to find expected output test.hello.txt"
[ ! -f test.hello2.txt ] && err "Failed to find expected output test.hello2.txt"
[ ! -f test.hello.world.txt ] && err "Failed to find expected output test.hello.world.txt"

true
