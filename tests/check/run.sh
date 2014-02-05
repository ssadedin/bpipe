source ../testsupport.sh

run

grep -q "It failed" test.out || err "Failed to find expected text 'It Failed' in output"

bpipe override hello > test.out

run 

grep -q "It failed" test.out && err "Found unexpected text 'It Failed' in output"

true
