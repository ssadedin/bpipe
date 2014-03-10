source ../testsupport.sh

bpipe run  -p batch_name=testsim -p sample_info=samples.txt test.groovy  > test.out 2>&1

exists testsim_simulated_cnvs/XXXXX_test.bam

# Run again should skip 

bpipe test  -p batch_name=testsim -p sample_info=samples.txt test.groovy   > test.out 2>&1

grep -q "Would execute" test.out && err "Found 'Would execute' indicating command would execute even when outputs were already created"

true


