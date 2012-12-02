source ../testsupport.sh

run test*.txt

[ `grep -c "Stage hello" test.out` == 3 ]  || err "Failed to find expected stage hello 3 times"

[ ! -f test.chr1.hello.csv ] && err "Failed to find expected output test.chr1.hello.csv"
[ ! -f test.chr2.hello.csv ] && err "Failed to find expected output test.chr2.hello.csv"
[ ! -f test.chr3.hello.csv ] && err "Failed to find expected output test.chr3.hello.csv"

[ ! -f test.chr11.hello.csv ] || err "Found unexpected output test.chr11.hello.csv"

true
