source ../testsupport.sh

cp bpipe.config.two bpipe.config

run
[ `grep -c "Hello.*world: small" test.out` == 3 ] || err "3 preallocated configs not used"

cp bpipe.config.oneonly bpipe.config

source ./cleanup.sh
run

[ `grep -c "Hello.*world: small" test.out` == 2 ] || err "2 preallocated configs not used"


source ./cleanup.sh
cp bpipe.config.matchbykey bpipe.config
run

[ `grep -c "Hello.*world: berry" test.out` == 2 ] || err "2 preallocated configs not used for berry"

[ `grep -c "Hello.*world: juice" test.out` == 1 ] || err "1 preallocated configs not used for juice"


# Test that if a too small walltime is on the config, then
# the job from the pool will not be used
source ./cleanup.sh
cp bpipe.config.walltime  bpipe.config
run

[ `grep -c "Hello.*world: berry" test.out` == 0 ] || err "0 preallocated configs not used for berry"
[ `grep -c "Hello.*world: juice" test.out` == 1 ] || err "1 preallocated configs not used for berry"

true
