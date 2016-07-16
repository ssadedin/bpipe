source ../testsupport.sh

run 


grep -q 'Pipeline Succeeded' test.out || err "Pipeline failed with empty parallelisation list"

grep -q 'Parallel segment will not execute because the list to parallelise over is empty' || err "Expected warning about empty list not printed"

true
