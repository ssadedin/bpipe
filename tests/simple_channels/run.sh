source ../testsupport.sh

run input1.txt input2.txt input3.txt

exists input1.hello.txt	input2.hello.txt	input3.hello.txt

grep -q  "Processing paul" test.out || err "Could not find paul in test output"
grep -q  "Processing bob" test.out || err "Could not find bob in test output"
grep -q  "Processing fred" test.out || err "Could not find fred in test output"

