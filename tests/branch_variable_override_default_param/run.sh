source ../testsupport.sh

run

grep -q fubar test.out || err "Branch variable did not override default value of parameter"

true
