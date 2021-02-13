source ../testsupport.sh

bg-bpipe run test.groovy   ;  

while [ ! -e .bpipe/run.pid ];
do
    printf "."
    sleep 1
done

echo

while [ ! -e hello.txt ];
do
    printf "#"
    sleep 1
done

echo

kill -9 `cat .bpipe/run.pid` 

bpipe retry test > test.out

grep -q 'Dirty files were found' test.out || err "Failed to detect dirty files"

rm hello.txt

bpipe retry test > test.out

grep -q 'Dirty files were found' test.out && err "Printed warning for dirty files even though they were cleaned up"

true

