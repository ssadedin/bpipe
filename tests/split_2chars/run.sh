source ../testsupport.sh


run 11D*.txt

[ `grep -c "Stage hello" test.out` == 3 ] || err "Incorrect number of parallel stages from file split: expected 3"

true
