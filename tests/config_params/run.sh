source ../testsupport.sh

run

grep -q 'Hello mars' test.out || err "Did not see expected Hello mars message"

bpipe run -p planet=jupiter test.groovy > test.out

grep -q 'Hello jupiter' test.out || err "Did not see expected Hello jupiter message"


