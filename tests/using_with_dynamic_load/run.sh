source ../testsupport.sh

run 

grep -q 'HELLO = false' test.out || err "Could not find version with HELLO=false"

grep -q 'HELLO = true' test.out || err "Could not find version with HELLO=true"

true

