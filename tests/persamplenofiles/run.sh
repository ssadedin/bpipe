source ../testsupport.sh

rm -f *align*

run 

grep -q "The pattern provided .* did not match any of the files" test.out || err "Failed to find expected error message"

true
