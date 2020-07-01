source ../testsupport.sh

run test.txt

grep -q 'Output is test.hello.world.csv' test.out  || err "Failed to find expected output"

true
