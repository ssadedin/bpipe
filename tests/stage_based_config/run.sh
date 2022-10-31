source ../testsupport.sh

run test.groovy

grep -q 'I will use 4 threads' test.out || err "Failed to see 4 threads as configured for stage hello"

bpipe run --env fast test.groovy > test.out 2>&1

grep -q 'I will use 8 threads' test.out || err "Failed to see 8 threads as configured for stage hello in fast mode"

bpipe run --env reallyfast test.groovy > test.out 2>&1

grep -q 'I will use 12 threads' test.out || err "Failed to see 12 threads as configured for command hi in reallyfast mode"


true
