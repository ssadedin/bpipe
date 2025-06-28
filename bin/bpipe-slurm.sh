#!/bin/bash
# vim: expandtab:ts=4

# Start, stop and get status of jobs running on a Slurm job scheduler.
#
# Usage:
#
# Starting a job (will print job ID on standard output):
#
#    COMMAND="foobar" NAME=test WALLTIME="00:01:00" PROCS=1 QUEUE=main JOBTYPE=single ./bpipe-slurm.sh start
#
# Stopping a job (given some job id "my_job_id")
#
#    ./bpipe-slurm.sh stop my_job_id
#
# Getting the status of a job (given some job id "my_job_id")
#
#    ./bpipe-slurm.sh status my_job_id
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

################################################################################
# Modified 2013
# Andrew Lonsdale

# Modified from bpipe-torque.sh
# Called from SlurmCommandExecutor.groovy, based on TorqueCommandExecutor.groovy 
#
# Approach is mimic the wrapper and shell script relationship, and replace 
# Torque commands with Slurm equivalents

################################################################################
################################################################################
# Modified 2014
# Simon Gladman
#
# Added support for modules in Slurm. (Give the config file a space separated
# list of required modules using the modules="module1 module2 ..." directive.)
#
# Example of required format in config file:
#
#   commands {
#   
#       //quick jobs 10 min and 4GB
#       small {
#           walltime="0:10:00"
#           memory="4"
#           modules="python-gcc/2.7.5 qiime-gcc/1.8.0"
#       }
#   }
#       
#   The modifications then alter the Slurm script produced by bpipe to add the 
#   correct "module load XXXX/x.x.x" directives at the appropriate place.
#
################################################################################

# This is what we call the program in user messages
program_name=bpipe-slurm

# exit codes:
SUCCESS=0
INCORRECT_FIRST_ARGUMENT=1 # must be start, stop, or status
MISSING_JOB_PARAMETER=2    # one of the env vars not defined
STOP_MISSING_JOBID=3       # stop command not given job id as parameter
STATUS_MISSING_JOBID=4     # status command not given job id as parameter
SCANCEL_FAILED=5              # scancel command returned non-zero exit status
SCONTROL_FAILED=6             # scontrol command returned non-zero exit status
SBATCH_FAILED=7              # sbatch command returned non-zero exit status
MKDIR_JOBDIR_FAILED=8
JOBTYPE_FAILED=9              # jobtype variable led to non-zero exit status

ESSENTIAL_ENV_VARS="COMMAND NAME"
OPTIONAL_ENV_VARS="WALLTIME PROCS QUEUE JOBDIR JOBTYPE MEMORY CUSTOM_SUBMIT_OPTS"
DEFAULT_BATCH_MEM=4096
DEFAULT_BATCH_PROCS=1
DEFAULT_WALLTIME="01:00:00" # one hour
DEFAULT_QUEUE=debug	#Queue is parition in slurm, will use this with -p
DEFAULT_JOBTYPE=single	#Should be single, smp or mpi

# Print a usage message
usage () {
   echo "usage: $program_name (start | stop ID | status ID)"
   echo "start needs these environment variables: $ESSENTIAL_ENV_VARS"
   echo "start will use these variables if defined: $OPTIONAL_ENV_VARS"
}

# Generate a SLURM script from parameters found in environment variables.
make_slurm_script () {
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

  # set the jobtype
   if [[ -z $JOBTYPE ]]; then
      JOBTYPE=$DEFAULT_JOBTYPE
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
      job_script_name="$JOBDIR/job.slurm"
   else
      job_script_name="job.slurm"
   fi

 # set the account, if needed
   if [[  ! -z $ACCOUNT ]]; then
        account="#SBATCH --account $ACCOUNT"
   fi

   # allow specification of mb, gb or none
   if [[ $MEMORY == *gb ]] || [[ $MEMORY == *GB ]] || [[ $MEMORY == *G ]] || [[ $MEMORY == *g ]];
   then
    	MEMORY=${MEMORY%gb}
    	MEMORY=${MEMORY%g}
    	MEMORY=${MEMORY%G}
    	MEMORY=${MEMORY%GB}
    	MEM_UNIT="GB"
   elif [[ $MEMORY == *mb ]] || [[ $MEMORY == *MB ]] || [[ $MEMORY == *M ]] || [[ $MEMORY == *m ]];
   then
    	MEMORY=${MEMORY%mb}
    	MEMORY=${MEMORY%m}
    	MEMORY=${MEMORY%M}
    	MEMORY=${MEMORY%MB}
    	MEM_UNIT="MB"
   else
        MEM_UNIT="gb" # default is to assume GB
   fi
   
   if [ ! -z "$GPUS" ];
   then
        gpus_request="#SBATCH --gpus=$GPUS"
   fi

   # handle the single, smp and mpi types specially
   case $JOBTYPE in
      single) if [[ -z $MEMORY ]]; then
                memory_request="#SBATCH --mem=${DEFAULT_BATCH_MEM}"
             else
                memory_request="#SBATCH --mem=${MEMORY}${MEM_UNIT}"
             fi
             if [[ -z $PROCS ]]; then
                procs_request="#SBATCH --ntasks=$DEFAULT_BATCH_PROCS"
             else
                procs_request=$(printf "#SBATCH --ntasks=$PROCS\n#SBATCH --nodes=1")
             fi
             command_prefix="";; # used in mpi only


      smp)   if [[ -z $MEMORY ]]; then
                memory_request=""
             else
                memory_request="#SBATCH --mem=${MEMORY}${MEM_UNIT}" 
             fi
             # the SMP queue never requests cores (it gets a single node), and has --exclusive flag
             # (this may be VLSCI specific)
             command_prefix="" # used in mpi only
             procs_request=$(printf "#SBATCH --nodes=1\n#SBATCH --exclusive");;

      mpi) if [[ -z $MEMORY ]]; then
                memory_request="#SBATCH --mem-per-cpu=${DEFAULT_BATCH_MEM}"
             else
                memory_request="#SBATCH --mem-per-cpu=${MEMORY}${MEM_UNIT}"
             fi
             if [[ -z $PROCS ]]; then
                procs_request="#SBATCH --ntasks=$DEFAULT_BATCH_PROCS"
             else
                procs_request="#SBATCH --ntasks=$PROCS"
             fi
             command_prefix="mpirun";;
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
   # Output masking unreliable at moment, stores the sbatch stdout and stderr in logs
   cat > $job_script_name << HERE
#!/bin/bash
#SBATCH --job-name=$NAME
$account
$memory_request
#SBATCH --time=$WALLTIME
$procs_request
$gpus_request
#SBATCH -p $QUEUE

set -o errexit

$mods_request

$command_prefix $COMMAND

$POST_CMD

HERE

   echo $job_script_name
}

# Launch a job on the queue.
start () {
   # create the job script
   job_script_name=`make_slurm_script`
   # check that the job script file exists
   if [[ -f $job_script_name ]]
      then
         # launch the job and get its id
         job_id_full=`sbatch $CUSTOM_SUBMIT_OPTS $job_script_name`
         sbatch_exit_status=$?
         if [[ $sbatch_exit_status -eq 0 ]]
            then
               # SLURM syntax: Submitted batch job <jobID>
               # strip all but numbers , which assumes remainder is job identifier
               #job_id_number=`echo $job_id_full | sed -n 's/\([0-9][0-9]*\).*/\1/p'`
               job_id_number=`echo $job_id_full | sed 's/[^0-9]//g'`
               echo $job_id_number
            else
               echo "$program_name ERROR: sbatch returned non zero exit status $sbatch_exit_status"
               exit $SBATCH_FAILED
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
         scancel "$1"
         scancel_success=$?
         if [[ $scancel_success == 0 ]]
            then
               exit $SUCCESS
            else
               exit $SCANCEL_FAILED
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
         # get the output of scontrol
         scontrol_output=`scontrol show job $1`
         scontrol_success=$?
         if [[ $scontrol_success == 0 ]]
         then
               job_state=`echo $scontrol_output|grep JobState|sed 's/.*JobState=\([A-Z_]*\) .*/\1/'` # JobState is in caps
               case "$job_state" in
                  CONFIGURING|PENDING|SUSPENDED) echo WAITING;; 
                  COMPLETING|RUNNING) echo RUNNING;;    
                  CANCELLED) echo COMPLETE 999;; # Artificial exit code because Slurm does not provide one    
                  TIMEOUT) echo COMPLETE 998;; # Artificial exit code because Slurm does not provide one    
                  OUT_OF_MEMORY) echo COMPLETE 997;;
                  COMPLETED|FAILED|NODE_FAIL|PREEMPTED) 
                  # scontrol will include ExitCode=N:M, where the N is exit code and M is signal (ignored)
                  #        command_exit_status=`echo $scontrol_output |grep Exit|sed 's/.*ExitCode=\([0-9]*\):[0-9]*/\1/'`
                  command_exit_status=`echo $scontrol_output|tr ' ' '\n' |awk -vk="ExitCode" -F"=" '$1~k{ print $2}'|awk -F":" '{print $1}'`

                  # it is possible that command_exit_status will be empty
                  # for example we start the job and then it waits in the queue
                  # and then will kill it without it ever running
                  echo "COMPLETE $command_exit_status";;

                  *) echo UNKNOWN;;
               esac
               exit $SUCCESS

          # it seems if scontrol doesn't know about the job id it returns 1 - but
          # this is not a specific  cerror code. 
          # this can happen on a legitimate job id when scontrol decides that
          # the job is too old to remember about
          elif [[ $scontrol_success == 1 ]]
          then
              errortext="slurm_load_jobs error: Invalid job id specified"
              if  [[ $scontrol_output == $errortext ]]
              then 
                  echo UNKNOWN
              else
                  # all other scontrol errors are treated as failures
                  exit $SCONTROL_FAILED
              fi
          else
               exit $SCONTROL_FAILED
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
