source ../testsupport.sh

bpipe run -m 2gb test.groovy > test.out

[ `grep -A 1 'will use' test.out | grep -c Done` -eq 4 ] || err "Failed to run 4 stages in series with memory limit"


bpipe run -m 12gb test.groovy > test.out

[ `grep -A 1 'will use' test.out | grep -c Done` -eq 1 ] || err "Failed to run 4 stages in parallel with high memory limit"


true

