#!/bin/bash

# Start, stop and get status of jobs running on a PBS PROFESSIONAL job scheduler.
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
# Inspired by bpipe-torque.sh from: Bernie Pope, Simon Sadedin, Alicia Oshlack
# Author: Davide Rambaldi <davide.rambaldi AT gmail DOT com>
# Copyright 2013.

# This is what we call the program in user messages
program_name=bpipe-pbspro

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

ESSENTIAL_ENV_VARS="COMMAND NAME PBSOUTPUT PBSERROR"
OPTIONAL_ENV_VARS="WALLTIME PROCS QUEUE JOBDIR MEMORY"
DEFAULT_BATCH_MEM=1
DEFAULT_BATCH_PROCS=1
DEFAULT_QUEUE=workq

# Utility to join strings with separator
function join { local IFS="$1"; shift; echo "$*"; }

# Print a usage message
usage () {
	echo "usage: $program_name (start | stop ID | status ID)"
	echo "start needs these environment variables: $ESSENTIAL_ENV_VARS"
	echo "start will use these variables if defined: $OPTIONAL_ENV_VARS"
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
	
	if [[ ! -z $PROJECT ]]; then
		project_name="#PBS -P ${PROJECT}"
	fi

	if [[  ! -z $ACCOUNT ]]; then
		account="#PBS -A $ACCOUNT"
	fi

	# cfr man pbs_resources
	# : Format:
    #       qsub ... -l <resource name>=<value>   <--- job-wide request
    #                -l select=<chunks>           <--- selection statement
    #   The only resources that can be requested in chunks are host-level resources, such as mem and ncpus.  
    #   The only resources that can be in a job-wide request are server-level or queue-level resources, such as walltime.  
    
    resource_requests=()

    # we have a SELECT_STATEMENT var
    if [[ ! -z $SELECT_STATEMENT ]]; then
    	select_request="#PBS -l select=$SELECT_STATEMENT"
    fi

	if [[ ! -z $WALLTIME ]]; then
		walltime_request="#PBS -l walltime=${WALLTIME}"
        resource_requests+=("walltime=${WALLTIME}")
	fi

	# First strip name beacuse in PBS pro -N name have the following specs:
	# Format: string, up to 15  characters  in  length.   
	# It must consist of an alphabetic or numeric character 
	# followed by printable, non-white-space characters.
	TRIMMED_NAME=$(echo $NAME | cut -c 1-15)
    
    if [[ ! -z $PROCS ]];
    then
        resource_requests+=("nodes=1:ppn=$PROCS")
        echo "PROCS=$PROCS" > tmp.log
    fi
    
    # ssadedin: add memory request
    if [[ ! -z $MEMORY ]];
    then
        memory_request="#PBS -l mem="`echo "$MEMORY" | sed 's/gb$//'`"gb"
        resource_requests+=("mem="`echo "$MEMORY" | sed 's/gb$//'`"gb")
    fi
    
    resource_requests_joined=`join , ${resource_requests[*]}`
    if [[ ! -z $resource_requests_joined ]];
    then
        resource_requests_directive="#PBS -l $resource_requests_joined"
    fi

	# write out the job script to a file
	cat > $job_script_name << HERE
#!/bin/bash
#PBS -N $TRIMMED_NAME
#PBS -q $QUEUE
#PBS -o $PBSOUTPUT
#PBS -e $PBSERROR
$project_name
$account
$resource_requests_directive
$select_request

cd \$PBS_O_WORKDIR
$COMMAND
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
		job_id_full=`qsub $job_script_name`
		qsub_exit_status=$?
		if [[ $? -eq 0 ]]
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
		if [[ qdel_success == 0 ]]
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
		# Davide Rambaldi: adding -x for PBS Professional engine
		qstat_output=`qstat -x -f "$1"`
		qstat_success=$?
		if [[ $qstat_success == 0 ]]
		then
			# XXX what to do if the awk fails?
			job_state=`echo "$qstat_output" | awk '/job_state =/ { print $3 }'`
			case "$job_state" in
				Q|H|W) echo WAITING;;   # job is in Queue or on Hold or Waiting for start time to arrive
				R|E) echo RUNNING;;     # if the job is exiting (E) will still think it is running
									    # XXX what to do if the awk fails?
				F) command_exit_status=`echo "$qstat_output" | awk '/Exit_status =/ { print $3 }'`
										# Davide Rambaldi: adding cases for PBS Professional engine: Finished status
										# is F in PBS professional and exit_status is Exit_status.
					echo "COMPLETE $command_exit_status";;
				*) echo UNKNOWN;;  		# there are other codes such as:
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
	  			exit $INCORRECT_FIRST_ARGUMENT;;
		esac
	else
		usage
		exit $INCORRECT_FIRST_ARGUMENT
	fi
	exit $SUCCESS
}

main "$@"
