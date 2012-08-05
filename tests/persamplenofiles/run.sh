source ../testsupport.sh

rm -f *align*

run 

grep -q "An input pattern was specified '.*' but no inputs were given when Bpipe was run" test.out || err "Failed to find expected error message"

true
