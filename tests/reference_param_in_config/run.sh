source ../testsupport.sh

run

grep -q 'Hello world Byebye' test.out || err 'Did not observe message with Hey'

bpipe run -p BAR=Hey test.groovy > test.out 2>&1

grep -q 'Hello world Hey' test.out || err 'Did not observe message with Hey'

true
