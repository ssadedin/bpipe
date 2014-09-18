source ../testsupport.sh

bpipe run -d testoutput test.groovy test.txt > test.out

exists testoutput/hello1.txt 

bpipe test -d testoutput test.groovy test.txt > test.out

grep -q 'Abort due to Test Mode' test.out && err 'Retry attempted to run command again even though outputs already created'

true
