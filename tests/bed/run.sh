
source ../testsupport.sh


run

grep -q 'Processing chr4:180-620' test.out || err "Failed to find expected region: chr4:180-620 in output"

[ `grep -c Processing test.out` -eq 4 ] || err "Incorrect number of splits used"

true
