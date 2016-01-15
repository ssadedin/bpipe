source ../testsupport.sh

run test.foo.txt

grep -q "1 The inputs.*test.foo.txt" test.out || err "Failed to probe using input.foo.txt"

grep -q "2 The inputs.*test.foo.txt" test.out || err "Failed to probe using inputs.foo.txt"

grep -q "No bar.txt input, excellent" test.out || err "Found an input incorrectly for .bar.txt"

grep -q "CORRECT, found regex pattern" test.out || err "Did not report finding match via regex pattern"

true

