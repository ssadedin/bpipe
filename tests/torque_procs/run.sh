source ../testsupport.sh

../../bin/bpipe run -n 8 test.groovy > test.out

grep -q procs=8 .bpipe/commandtmp/*/*.pbs || err "Failed to find procs correctly passed through to job submission"

