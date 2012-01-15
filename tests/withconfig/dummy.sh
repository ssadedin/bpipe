#!/bin/bash 
# A very minimal test script
echo "ARG1=$1"
if [ "$1" == "start" ];
then
  echo "Starting $*"
  bash -c $2 $COMMAND &
  echo $!
elif [ "$1" == "stop" ];
then
  sleep 1
  echo "COMPLETE"
elif [ "$1" == "stop" ];
then
  echo "Stopping $1"
  kill $1
fi
