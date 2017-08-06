source ../testsupport.sh

cp bpipe.config.dynamic bpipe.config
../../bin/bpipe run -n 8 test.groovy > test.out
grep -q procs=8 .bpipe/commandtmp/*/*.pbs || err "Failed to find dynamic procs correctly passed through to job submission"


cp bpipe.config.fixed bpipe.config
../../bin/bpipe run -n 8 test.groovy > test.out
grep -q procs=1 .bpipe/commandtmp/*/*.pbs || err "Failed to find dynamic procs correctly passed through to job submission"


cp bpipe.config.explicitdynamic bpipe.config
../../bin/bpipe run -n 8 test.groovy > test.out
grep -q procs=8 .bpipe/commandtmp/*/*.pbs || err "Failed to find dynamic procs correctly passed through to job submission"



