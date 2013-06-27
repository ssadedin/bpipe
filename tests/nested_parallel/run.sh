source ../testsupport.sh

source ./cleanup.sh

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"
grep -q "Stage end" test.out || err "Failed to find expected stage end"

[ ! -f test.txt.hello ] && err "Failed to find expected output test.txt.hello"
[ ! -f test.txt.hello.world ] && err "Failed to find expected output test.txt.hello.world"
[ ! -f test.txt.hello.take_me_to_your_leader ] && err "Failed to find expected output test.txt.hello.take_me_to_your_leader"
[ ! -f test.txt.hello.how_are_you ] && err "Failed to find expected output test.txt.hello.how_are_you"

grep -q "goo" *.end || err "Failed to find goo in output as expected from test.txt.hello.take_me_to_your_leader"
grep -q "foo" *.end || err "Failed to find foo in output as expected from test.txt.hello"
grep -q "boo" *.end || err "Failed to find boo in output as expected from test.txt.hello.how_are_you"

true
