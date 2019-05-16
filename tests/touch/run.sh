source ../testsupport.sh

run test.txt  

sleep 2; 

touch test.txt;  

grep -q '\->' test.out || err 'Expected commands did not execute' 

bpipe touch test.groovy test.txt  > test2.out 2>&1

grep -q '\->' test2.out && err 'Command re-executed unexpectedly when bpipe touch was run' 

grep -q 'passing check' test2.out && 'Check re-executed when touched unexpectedly'

true
