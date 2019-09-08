source ../testsupport.sh

bpipe run test.groovy > test.out 2>&1 &

sleep 1

export BPIPE_QUIET=true

bpipe run test.groovy > test.out2 2>&1 &

sleep 3

grep -q "Quiet mode enabled: auto-aborting this pipeline" test.out2 || err "Failed to find auto abort message when quiet mode enabled"

kill %1 %2

true
