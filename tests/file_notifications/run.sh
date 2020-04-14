source ../testsupport.sh

run

grep -q Hello notifications/*.txt || err "Failed to create expected notification file"

true

