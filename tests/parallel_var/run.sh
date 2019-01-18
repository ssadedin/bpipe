source ../testsupport.sh

run

grep -q 'counts > 1: 0' test.out || err "Expected message confirming no duplicated variables was not printed"

true
