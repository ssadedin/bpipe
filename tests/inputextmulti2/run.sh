source ../testsupport.sh

run test_*.txt test.csv

grep -q "inputs.txt: cat test_1.txt test_2.txt > test_1.txt.hello" test.out || err "Failed to find expected output 'inputs.txt: cat test_1.txt test_2.txt > test_1.txt.hello'"
grep -q "input.txt:  cat test_1.txt > test_1.txt.hello" test.out || err "Failed to find expected output 'input.txt:  cat test_1.txt > test_1.txt.hello'"
grep -q "inputs:  cat test_1.txt test_2.txt test.csv > test_1.txt.hello" test.out || err "Failed to find expected output 'inputs:  cat test_1.txt test_2.txt test.csv > test_1.txt.hello'"
grep -q "input:  cat test_1.txt > test_1.txt.hello" test.out || err "Failed to find expected output 'input:  cat test_1.txt > test_1.txt.hello'"
grep -q "input.0.:  cat test_1.txt > test_1.txt.hello" test.out || err "Failed to find expected output 'input[0]:  cat test_1.txt > test_1.txt.hello'"
grep -q "inputs.0.:  cat test_1.txt > test_1.txt.hello" test.out || err "Failed to find expected output 'inputs[0]:  cat test_1.txt > test_1.txt.hello'"

true
