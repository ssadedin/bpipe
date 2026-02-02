#!/bin/bash

function usage() {
      echo "Usage: $0 [options]"
      echo
      echo "-s <sample id> :   sample id"
      echo "-d <dir>       :   directory containing pod5 files" 
      echo ""
      exit 0
}

while getopts "s:d:h" arg; do
  case $arg in
    s) # sample id
      export sample_id=$OPTARG
      ;;
    d) # data
      export data_path=$OPTARG
      ;;
     h | *) # Display help.
         usage
      ;;
    :)
      echo "Parameter -$OPTARG requires a positional argument"
      exit 1
      ;;
  esac
done

if [ -z "$sample_id" ] || [ -z "$data_path" ];
then
    echo "Please specify sample and data path"

    usage
fi

PREFIX=.

if [ -e batches ];
then
	PREFIX=./batches
fi

BATCH_DIR=$PREFIX/${sample_id}

mkdir $BATCH_DIR

echo '
samples:
  - identifier: $sample_id
    familyId: $sample_id
    sex: female
    inputs:
        - $data_path
    parents: null

' | envsubst | tee $BATCH_DIR/samples.yaml


echo
echo "Done."
echo
echo "
To run this:

cd $BATCH_DIR

# TODO: ADJUST SEX OF SAMPLES in samples.yaml

bpipe run -p ANALYSIS_QUAL=hac --env ont_server /storage/warpy/src/pipeline.groovy -targets /storage/warpy/designs/WGS_REFFLAT_10_NOALT_38/WGS_REFFLAT_10_NOALT_38.bed -samples samples.yaml
"
