source ../testsupport.sh

bpipe run test.groovy test.tab > test.out 2>&1


grep -q -v 'No signature of method' test.out || err "Bad error reported for transform with 4 args"

true

