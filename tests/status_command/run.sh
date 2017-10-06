source ../testsupport.sh


bg-bpipe run test.groovy

sleep 2

bpipe status > status.out 2>&1

grep -q 'Found 1 currently executing commands:' status.out || err "Status failed to show expected message: Found 1 currently executing commands"

grep -q 'echo "Hello there, I am sleeping for 5 seconds"' status.out || err "Status failed to show command"

true

