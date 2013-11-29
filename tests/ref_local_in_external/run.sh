source ../testsupport.sh

run

grep -q "hello dog" test.out || err "Did not find expected variable 'dog' in test output"

true
