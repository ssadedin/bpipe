source ../testsupport.sh

run

grep -q "WARNING: 1 check(s) failed" test.out || err "Check did not fail as expected"

true
