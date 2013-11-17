source ../testsupport.sh

run

grep -q "^mars" test.out || err "Failed to find mars printed out"
grep -q "^world" test.out || err "Failed to find world printed out"
grep -q "^jupiter" test.out || err "Failed to find mars printed out"

true
