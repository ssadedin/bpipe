source ../testsupport.sh

run 

grep -q "Command failed" test.out && err "Failed to execute bash command terminated with ampersands"

true
