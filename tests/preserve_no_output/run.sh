source ../testsupport.sh

run test.bam

grep -q 'Pipeline Succeeded' test.out || err "Failed to find pipeline succeeeded message"

bpipe query test.bam.bai | grep -q 'Preserved.*yes' || err "Did not see output as preserved from bpipe query"

true
