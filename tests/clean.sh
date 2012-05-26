#!/bin/bash

# Clean up standard unwanted files
for i in .bpipe test.out commandlog.txt doc ;
do
	find . -name "$i" | xargs rm -rf
done

# Run individual cleanup scripts
for i in `find . -name cleanup.sh`; do cd `dirname $i`; ./cleanup.sh; cd - ; done

