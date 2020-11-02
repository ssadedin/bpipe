source ../testsupport.sh

run test.txt

grep -q "Pipeline Succeeded" test.out || err "Failed to run pipeline successfully with optional transform stage"

true

