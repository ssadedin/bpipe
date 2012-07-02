source ../testsupport.sh

rm -f test.txt.*

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

run test.txt

grep -q "Skipping execution" test.out || err "Failed to find stage being skipped on second run"


[ ! -f test.hello.csv ] && err "Failed to find expected output test.hello.csv"
[ ! -f test.hello.world.txt ] && err "Failed to find expected output test.hello.world.txt"
[ ! -f test.hello.world.csv ] && err "Failed to find expected output test.hello.world.csv"
[ ! -f test.bar ] && err "Failed to find expected output test.bar - should have been created because of produce"

true
