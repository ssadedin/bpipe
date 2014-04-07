source ../testsupport.sh

run test.txt

exists test.fubar.txt

grep -q "Expected output file" test.out && err "Failed to accept referenced input as basis for filter"

true
