source ../testsupport.sh

run test.txt

exists out/test.hello.csv

bpipe query out/test.hello.csv > test.out 2>&1

grep -q "Command:" test.out || err "Failed to find expected output of query command"

true

