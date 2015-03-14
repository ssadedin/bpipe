source ../testsupport.sh

run test.groovy

grep -q 'Hello bar' test.out || err "Branch a didn't produce the right message"
grep -q 'Hello fubar' test.out || err "Branch b didn't produce the right message"
grep -q 'I like to frobble' test.out || err "Branch a didn't call the right function"
grep -q 'I DONT like to frobble' test.out || err "Branch b didn't call the right function"

true
