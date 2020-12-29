source ../testsupport.sh

rm -rf .bpipe

bpipe run -L test.bed test.groovy > test.out

grep -q "chr5:1000-2000" test.out || err "Did not run on expected region"

grep -q 'chr5.*1000.*2000' .bpipe/regions/*.bed || err "Created bed file did not include expected region"

grep -q 'Pipeline Succeeded' test.out || err "Pipeline did not succeed - was region bed file generated?"

true

