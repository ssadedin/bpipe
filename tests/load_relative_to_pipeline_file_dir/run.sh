source ../testsupport.sh

bpipe run dir/test.groovy > test.out

grep -q world test.out || err "Failed to find expected output 'world' in output"

true

