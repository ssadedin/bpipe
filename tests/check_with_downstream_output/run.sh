source ../testsupport.sh

run

[ -e hello.txt ] || err 'could not find expected output hello.txt'

run 

grep  -q 'The inputs are hello.txt' test.out || err 'Failed to find correct input resolved from upstream check output'

true

