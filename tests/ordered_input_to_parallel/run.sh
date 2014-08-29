source ../testsupport.sh

run 

grep -q "inputs should not be equal" test.out && err "Input referenced multiple times"

true
