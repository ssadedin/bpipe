#!/bin/bash

FILTER="$1"

if [ ! -z "$FILTER" ];
then
    TESTS=`find . -maxdepth 1 -type d | grep -E "^\.\/[A-Za-z]" | grep $FILTER`
else
    TESTS=`find . -maxdepth 1 -type d | grep -E "^\.\/[A-Za-z]"`
fi

source testsupport.sh

succ=0
fail=0
failures=""

for t in $TESTS;
do
	echo "============== $t ================"
	cd "$BASE"/"$t"
	rm -rf .bpipe

    if [ ! -e ./run.sh ];
    then
        echo "Skip $t : no run.sh present"
	elif ./run.sh;
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
    failures="$failures\n$t"
	fi
done

echo 
echo "=========== Summary ==========="
echo
echo "Success: $succ"
echo "Fail:    $fail"
echo
if [ $fail -gt 0 ]; 
then
  echo "Failed tests:"
  printf "$failures"
  echo
fi
exit $fail
