source ../testsupport.sh

run somedir/test.bed

grep -q "The input is somedir/test.bed" test.out || err "Correct input not resolved from extension as whole file name"

true
