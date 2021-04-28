#!/bin/bash

# Start, stop and get status of jobs running on a Torque job scheduler.
#
# Usage:
#
# Starting a job (will print job ID on standard output):
#
#    COMMAND="foobar" NAME=test WALLTIME="00:01:00" PROCS=1 QUEUE=batch ./bpipe-torque.sh start
#
# Stopping a job (given some job id "my_job_id")
#
#    ./bpipe-torque.sh stop my_job_id
#
# Getting the status of a job (given some job id "my_job_id")
#
#    ./bpipe-torque.sh status my_job_id
#
# Notes:
#
# None of the commands are guaranteed to succeed. An exit status of 0 for this script
# indicates success, all other exit codes indicate failure (see below).
#
# Stopping a job may not cause it to stop immediately. You are advised to check the
# status of a job after asking it to stop. You may need to poll this value.
#
# We are not guaranteed to know the exit status of a job command, for example if the
# job was killed before the command was run.
#
# Authors: Bernie Pope, Simon Sadedin, Alicia Oshlack
# Copyright 2011.

# This is what we call the program in user messages
program_name=bpipe-torque

# exit codes:
SUCCESS=0
INCORRECT_FIRST_ARGUMENT=1 # must be start, stop, or status
MISSING_JOB_PARAMETER=2    # one of the env vars not defined
STOP_MISSING_JOBID=3       # stop command not given job id as parameter
STATUS_MISSING_JOBID=4     # status command not given job id as parameter
QDEL_FAILED=5              # qdel command returned non-zero exit status
QSTAT_FAILED=6             # qstat command returned non-zero exit status
QSUB_FAILED=7              # qsub command returned non-zero exit status
MKDIR_JOBDIR_FAILED=8      # mkdir $JOBDIR failed

ESSENTIAL_ENV_VARS="COMMAND NAME"
OPTIONAL_ENV_VARS="WALLTIME PROCS QUEUE JOBDIR MEMORY CUSTOM_SUBMIT_OPTS GPUS"
DEFAULT_BATCH_MEM=1
DEFAULT_BATCH_PROCS=1
DEFAULT_WALLTIME="01:00:00" # one hour
DEFAULT_QUEUE=batch

# Default to 1 node - this can be overridden directly by user
# or alternatively by value of procs via set_procs
: ${NODES:=1}

# Print a usage message
usage () {
   echo "usage: $program_name (start | stop ID | status ID)"
   echo "start needs these environment variables: $ESSENTIAL_ENV_VARS"
   echo "start will use these variables if defined: $OPTIONAL_ENV_VARS"
}

#
# How procs are set depends on PROC_MODE environment variable
# If set to PPN then we use the form ppn attribute, otherwise
# default to procs. Note that procs is not supported in raw
# PBS torque.
#
set_procs() {
    if [ -z "$PROC_MODE" ];
    then
        procs_request="#PBS -l procs=$1"
    else
        procs_request="#PBS -l nodes=$NODES:ppn=$1"
    fi
    
    if [ ! -z "$GPUS" ];
    then
        procs_request="$procs_request:gpus=$GPUS"
    fi
}

# Generate a PBS script from parameters found in environment variables.
make_pbs_script () {
   # check that all the essential environment variables are defined
   for v in $ESSENTIAL_ENV_VARS; do
      eval "k=\$$v" 
      if [[ -z $k ]]; then
         echo "$program_name ERROR: environment variable $v not defined"
         echo "these environment variables are required: $ESSENTIAL_ENV_VARS"
         exit $MISSING_JOB_PARAMETER
      fi
   done

   # set the walltime
   if [[ -z $WALLTIME ]]; then
      WALLTIME=$DEFAULT_WALLTIME
   fi

   # set the queue
   if [[ -z $QUEUE ]]; then
      QUEUE=$DEFAULT_QUEUE
   fi

   # set the job directory if needed
   if [[ -n $JOBDIR ]]; then
      # check if the directory already exists
      if [[ ! -d "$JOBDIR" ]]; then
         # try to make the directory
         mkdir "$JOBDIR"
         # check if the mkdir succeeded
         if [[ $? != 0 ]]; then
            echo "$program_name ERROR: could not create job directory $JOBDIR"
            exit $MKDIR_JOBDIR_FAILED
         fi
      fi
      job_script_name="$JOBDIR/job.pbs"
   else
      job_script_name="job.pbs"
   fi
   
   # set the account, if needed
   if [[  ! -z $ACCOUNT ]]; then
        account="#PBS -A $ACCOUNT"
   fi

   # allow specification of mb, gb or none
   if [[ $MEMORY == *gb ]] || [[ $MEMORY == *GB ]] || [[ $MEMORY == *G ]] || [[ $MEMORY == *g ]];
   then
    	MEMORY=${MEMORY%gb}
    	MEMORY=${MEMORY%g}
    	MEMORY=${MEMORY%G}
    	MEMORY=${MEMORY%GB}
    	MEM_UNIT="gb"
   elif [[ $MEMORY == *mb ]] || [[ $MEMORY == *MB ]] || [[ $MEMORY == *M ]] || [[ $MEMORY == *m ]];
   then
    	MEMORY=${MEMORY%mb}
    	MEMORY=${MEMORY%m}
    	MEMORY=${MEMORY%M}
    	MEMORY=${MEMORY%MB}
    	MEM_UNIT="mb"
   else
        MEM_UNIT="gb" # default is to assume GB
   fi

   # handle the batch and smp queues specially with regards to memory and procs
   case $QUEUE in
      batch) 
             : ${MEM_PARAM:=pvmem}
             if [[ -z $MEMORY ]]; then
                memory_request="#PBS -l $MEM_PARAM=${DEFAULT_BATCH_MEM}gb"
             else
                memory_request="#PBS -l $MEM_PARAM=${MEMORY}${MEM_UNIT}"
             fi
             if [[ -z $PROCS ]]; then
                set_procs $DEFAULT_BATCH_PROCS
             else
                set_procs $PROCS
             fi;;
      smp)   if [[ -z $MEMORY ]]; then
                memory_request=""
             else
                : ${MEM_PARAM:=mem}
                memory_request="#PBS -l $MEM_PARAM=${MEMORY}${MEM_UNIT}" 
             fi
             # the SMP queue never requests cores (it gets a single node)
             procs_request="";;

       *)    if [[ -z $MEMORY ]]; then
                memory_request=""
             else
                : ${MEM_PARAM:=mem}
                memory_request="#PBS -l $MEM_PARAM=${MEMORY}${MEM_UNIT}" 
             fi 
             if [[ -z $PROCS ]]; then
                #procs_request="#PBS -l procs=$DEFAULT_BATCH_PROCS"
                set_procs $DEFAULT_BATCH_PROCS
             else
                #procs_request="#PBS -l procs=$PROCS"
                set_procs $PROCS
             fi
             ;;
   esac
   
    mods_request=""
    #handle the module specifications. - Simon Gladman 2014
    if [[  ! -z $MODULES ]]; then
        for MOD in $MODULES
        do
            mods_request="${mods_request}
module load $MOD"
        done
    fi

   # write out the job script to a file
   cat > $job_script_name << HERE
#!/bin/bash
#PBS -N $NAME
$account
$memory_request
#PBS -l walltime=$WALLTIME
$procs_request
#PBS -q $QUEUE
$CUSTOM
set -e;
$mods_request
cd \$PBS_O_WORKDIR
$COMMAND

$POST_CMD

HERE

   echo $job_script_name
}

# Launch a job on the queue.
start () {
   # create the job script
   job_script_name=`make_pbs_script`
   # check that the job script file exists
   if [[ -f $job_script_name ]]
      then
         # launch the job and get its id
         job_id_full=`qsub $CUSTOM_SUBMIT_OPTS $job_script_name`
         qsub_exit_status=$?
         if [[ $qsub_exit_status -eq 0 ]]
            then
               # bite off the job number from the start of the job identifier
               job_id_number=`echo $job_id_full | sed -n 's/\([0-9][0-9]*\).*/\1/p'`
               echo $job_id_number
            else
               echo "$program_name ERROR: qsub returned non zero exit status $qsub_exit_status"
               exit $QSUB_FAILED
         fi
      else
         echo "$program_name ERROR: could not create job script $job_script_name"
   fi
}

# stop a job given its id
# XXX should we check the status of the job first?
stop () {
   # make sure we have a job id on the command line
   if [[ $# -ge 1 ]]
      then
         # try to stop it
         qdel "$1"
         qdel_success=$?
        if [[ $qdel_success == 0 ]]
            then
               exit $SUCCESS
            else
               exit $QDEL_FAILED
         fi
      else
         echo "$program_name ERROR: stop requires a job identifier"
         exit $STOP_MISSING_JOBID
   fi
}

# get the status of a job given its id
status () {
   # make sure we have a job id on the command line
   if [[ $# -ge 1 ]]
      then
         # look at the output of qstat
         qstat_output=`qstat -f "$1"`
         qstat_success=$?
         if [[ $qstat_success == 0 ]]
            then
               # XXX what to do if the awk fails?
               job_state=`echo "$qstat_output" | awk '/job_state =/ { print $3 }'`
               case "$job_state" in
                  Q|H|W) echo WAITING;; # job is in Queue or on Hold or Waiting for start time to arrive
                  R|E) echo RUNNING;;    # if the job is exiting (E) will still think it is running
                  # XXX what to do if the awk fails?
                  C) command_exit_status=`echo "$qstat_output" | awk '/exit_status =/ { print $3 }'`
                     # it is possible that command_exit_status will be empty
                     # for example we start the job and then it waits in the queue
                     # and then will kill it without it ever running
                     echo "COMPLETE $command_exit_status";;
                  *) echo UNKNOWN;;  # there are other codes such as:
                                     #   T (job being moved) 
               esac
               exit $SUCCESS
            # it seems if qstat doesn't know about the job id it returns 153
            # this can happen on a legitimate job id when qstat decides that
            # the job is too old to remember about
            elif [[ $qstat_success == 153 ]]
               then
                  echo UNKNOWN
            # all other qstat errors are treated as failures
            else
               exit $QSTAT_FAILED
         fi
      else
         echo "$program_name ERROR: status requires a job identifier"
         exit $STATUS_MISSING_JOBID
   fi
}

# run the whole thing
main () {
   # check that we have at least one command
   if [[ $# -ge 1 ]]
      then
         case "$1" in
            start)  start;;
            stop)   shift
                      stop "$@";;
            status) shift
                      status "$@";;
            *) usage
               exit $INCORRECT_FIRST_ARGUMENT
            ;;
         esac
      else
         usage
         exit $INCORRECT_FIRST_ARGUMENT
   fi
   exit $SUCCESS
}

main "$@"
