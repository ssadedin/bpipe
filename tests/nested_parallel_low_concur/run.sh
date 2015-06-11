source ../testsupport.sh

bpipe run -n 5 test.groovy  > test.out

grep -q 'n / 28' test.out || err "Did not find expected stage output n / 28"

true
