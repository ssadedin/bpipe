source ../testsupport.sh

run

grep -q 'wrong value' test.out && err "Variable FOO had wrong value"
grep -q 'correct value' test.out || err "Variable FOO had wrong value"

true
