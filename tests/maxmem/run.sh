source ../testsupport.sh

bpipe run -m 6100 test.groovy > test.out 2>&1

grep -A 1 Hello test.out | awk '{ if(NR % 2 == 0) print $0 }' | grep -q Hello && err "Only World should follow Hello in output"

true


