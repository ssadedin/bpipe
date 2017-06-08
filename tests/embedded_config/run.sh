source ../testsupport.sh


bpipe test test.groovy > test.out


grep -q 'executor:torque, memory:5g' test.out || err "Failed to find correct memory setting for torque"


true
