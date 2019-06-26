source ../testsupport.sh

run 

grep -q 'Pipeline Succeeded' test.out || err "Pipeline failed when accessing delayed file"


