source ../testsupport.sh

run test.txt  test2.txt  test.csv

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "inputs:  test.txt test2.txt test.csv" test.out || err "Failed to find expected output: inputs:  test.txt test2.txt test.csv"

true
