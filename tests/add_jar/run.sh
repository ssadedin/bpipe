source ../testsupport.sh

run test.groovy

grep -q 'The magic foo value is 10' test.out || err "Failed to find expected message 'The magic foo value is 10' from custom class in output"

true
