#!/bin/bash

TESTS=`find . -maxdepth 1 -type d | grep "[A-Za-z]"`

BASE=`pwd`

succ=0
fail=0

function err() {
	echo
	echo "MSG: $1"
	echo
}

# Convenience function to run the test
function run() {
	bpipe run test.groovy $* > test.out 2>&1
}

# Convenience function to run in test mode
function runtest() {
	bpipe test test.groovy $* > test.out 2>&1
}

for t in $TESTS;
do
	echo "============== $t ================"
	cd "$BASE"/"$t"
	rm -rf .bpipe
	
	if . run.sh;
	then
		echo
		echo "SUCCEEDED"
		echo
		let 'succ=succ+1'
	else
		echo
		echo "FAILED"
		echo
		let 'fail=fail+1'
	fi
done
