source ../testsupport.sh

cp bpipe.config.two bpipe.config

run
[ `grep -c "Hello.*world: small" test.out` == 2 ] || err "2 preallocated configs not used"

cp bpipe.config.oneonly bpipe.config

run
[ `grep -c "Hello.*world: small" test.out` == 1 ] || err "1 preallocated configs not used"

cp bpipe.config.matchbykey bpipe.config

run
[ `grep -c "Hello.*world: berry" test.out` == 1 ] || err "1 preallocated configs not used for berry"

[ `grep -c "Hello.*world: juice" test.out` == 1 ] || err "1 preallocated configs not used for juice"



true
