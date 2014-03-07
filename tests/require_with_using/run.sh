source ../testsupport.sh

run 

grep -q "hello mars" test.out || err "Failed to find 'hello mars' in output"

true
