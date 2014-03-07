source ../testsupport.sh

run

grep -q "x = 15 and y = 20" test.out || err "Did not find expected values x = 15 and y = 20 in output"

grep -q "Pipeline stage world requires a parameter y" test.out || err "Did not find expected text 'Pipeline stage world requires a parameter y' in output"

true
