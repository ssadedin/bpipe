#!/bin/bash 
# A very minimal test script
if [ "$1" == "start" ];
then
  #echo "Starting $*"
  echo "$COMMAND" >> dummy2.log.txt
  bash -c "if $COMMAND ; then echo 0 > $$.txt ; else echo 1 > $$.txt; fi" &
  PROCID=$!
  echo $PROCID
  echo $$ > $PROCID.id.txt
elif [ "$1" == "status" ];
then
  SHELL_PROC_ID=`cat $2.id.txt`
  if [ -f $SHELL_PROC_ID.txt ];
  then
    echo "COMPLETE "`cat $SHELL_PROC_ID.txt`;
  else
    echo "RUNNING"
  fi
elif [ "$1" == "stop" ];
then
  echo "Stopping $1"
  kill $1
else
  echo "Unknown command $1" 1>&2
  exit 1
fi
