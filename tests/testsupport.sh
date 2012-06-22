# Add BPipe from the project root to the path
if [ -z "$BASE" ];
then
	BASE=`pwd`
	if [ ! -e $BASE/../bin/bpipe ] && [ -e $BASE/../../bin/bpipe ] ;
	then
		BASE=`pwd`/..
	fi
fi

PATH="$BASE/../bin:$PATH"
which bpipe
if [ $? -ne 0  ]; then 
  echo Cannot find the 'bpipe' launch script on the path. Please make sure that 'BPIPE_ROOT/bin/bpipe' script has execute permission.
  exit 1
fi 


function err() {
	echo
	echo "ERROR: $1"
	echo
	exit 1
}

# Convenience function to run the test
function run() {
	bpipe run -r test.groovy $* > test.out 2>&1
}

# Convenience function to run in test mode
function runtest() {
	bpipe test test.groovy $* > test.out 2>&1
}
