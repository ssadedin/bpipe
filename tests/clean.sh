#!/bin/bash
for i in .bpipe test.out commandlog.txt ;
do
	find . -name "$i" | xargs rm -rf
done
