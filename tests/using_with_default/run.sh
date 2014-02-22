source ../testsupport.sh

run

grep -q "x = 15 and y = 20" test.out || err "Did not find expected values x = 15 and y = 20 in output"

grep -q "Variable or parameter 'x' was not specified" test.out || err "Did not find expected text 'Variable or parameter 'x' was not specified' in output"

true
