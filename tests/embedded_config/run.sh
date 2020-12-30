source ../testsupport.sh


bpipe test test.groovy > test.out


grep -q 'executor:torque' test.out || err "Failed to find correct executor setting for torque"

grep -q 'memory:5g' test.out || err "Failed to find correct memory setting for command"


true
