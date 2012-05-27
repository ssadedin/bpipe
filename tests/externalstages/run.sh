source ../testsupport.sh

BPIPE_LIB_OLD="$BPIPE_LIB"

export BPIPE_LIB=`pwd`/bpipes


uname -a | grep -i -q cygwin && {
  export BPIPE_LIB=`cygpath -ma $BPIPE_LIB`
}

echo "Using BPIPE_LIB=$BPIPE_LIB"

rm -f test.world.txt test.txt

run

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

[ ! -f test.txt ] && err "Failed to find expected output test.txt"
[ ! -f test.world.txt ] && err "Failed to find expected output test.world.txt"

export BPIPE_LIB="$BPIPE_LIB_OLD"

true
