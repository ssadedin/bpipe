source ../testsupport.sh

run test.txt test.xml

grep -q 'could not be found' test.out && err "Output was not resolved from filter in pipeline, even though correct"

grep -q 'Pipeline Succeeded' test.out || err "Pipeline failed but should have succeeded"

true
