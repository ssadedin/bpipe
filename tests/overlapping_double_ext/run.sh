source ../testsupport.sh

run

exists test.cov.world.there.txt || err "Expected output test.cov.world.there.txt not found"

true
