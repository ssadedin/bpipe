source ../testsupport.sh

run test.bar.txt

grep -q "Expected one or more inputs with extension 'bar.txt'" test.out || err "Failed to find expected error message due to missing input"

true
