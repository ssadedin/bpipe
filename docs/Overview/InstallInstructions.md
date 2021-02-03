# Install Instructions

## Installation

Bpipe is entirely self contained and doesn't require any installation.
Download the zip archive from the release page.  Unzip your Bpipe
download to a convenient location and place the "bin" directory in
your PATH.


## Supported Operating Systems

Bpipe is tested and maintained on:
- Linux RHEL, Centos and Ubuntu
- Mac OS X Snow Leopard or later
- Windows using Cygwin (`**`)

(`**`) Windows support may be limited in some instances due to limitations of Cygwin.

Although other Unix-like operating systems are welcome and very
likely will run Bpipe without problems, you are encouraged to check
bugs on one of the above OSes before asking for help.   Volunteers
to maintain more operating systems are welcome!

## Building from source

On Linux, Bpipe can be built from source provided the Java 8 JDK
is installed.  Once you have the source code (from git, or having
downloaded a released source archive), you can build it by invoking
`gradlew` from the top level directory of the source:

```
cd bpipe
./gradlew build
```

This will download all the necessary dependencies and build the jar
files. You can then put bpipe in your PATH, and you're all set!

If you get unit test failures that you wish to ignore, you can
disable the tests by invoking:

```
./gradlew build -x test
```
