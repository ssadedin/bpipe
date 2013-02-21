
source ../testsupport.sh

run test*.txt

grep -q "inputs are.*test1.txt.*test2.txt" test.out || err "Failed to find references to split files in output" 

true
