# How to set up an environment to develop or debug Bpipe

## Helping Out

Bpipe is an open source project that anybody can contribute to. There are a
variety of ways you can help, from adding documentation to providing feedback,
but most of all, if you have skills in programming, you can help add
new features to Bpipe.

## Building Bpipe

While to run Bpipe you need only a Java Runtine Environment installed, to build Bpipe you need a couple more things:

1. Install [git](http://git-scm.com/) if you don't already have it
1. Clone the Bpipe source code using a command such as:
```groovy 

    git clone https://github.com/ssadedin/bpipe
```

1. Download and install the [Java JDK](http://www.oracle.com/technetwork/java/javase/downloads/index-jsp-138363.html), add it to your PATH

Once you have these dependencies, you can go to the top directory that you cloned and type:
```groovy 

./gradlew dist
```

to compile and build Bpipe.  A Bpipe distribution will be created in the `build` directory, however you can run Bpipe directly from the distribution directory by typing
```groovy 

./bin/bpipe
```

This should allow you to make changes to the Bpipe source code and to compile and create your own Bpipe distributions to use.

*Note*: in the past, it was necessary to first install Gradle and to download the JavaMail jar file. This is no longer necessary, as both are downloaded as automatically as needed (note that you must use the 'gradlew' command in the distribution directory rather than any build in installed gradle for this to work).

## Running Tests

If you do make some changes you'll probably want (or at least, *should* want) to check if you broke some existing functionality. To do this, you can run the regression tests which live in the `tests` directory of the source distribution.  To run the tests, change to the tests directory and just execute the "run.sh" command:
```groovy 

cd tests
./run.sh
```

The tests will take about 5 minutes to run. Please note that one test depends on having R installed, so if you do not have that you can expect a failure in that test.

## Debugging

More than likely if you make any significant changes you will end up wanting to debug your code. The recommended way to do this is to launch Bpipe using 'debug' in place of 'run':
```groovy 

bpipe debug test.groovy input1.txt ....
```

The debug command will run Java in debug mode which will then wait for you to
attach with a debugger that supports remote debugging such as eclipse. Just
configure Eclipse to attach to the port printed out by the command.

## Adding Documentation

Improving Bpipe's documentation is one of the easiest ways to help. Even small
improvements such as clarifications, grammar fixes, or addition of examples are
very much appreciated.

See [DocumentationStyle](/Guides/DocumentationStyle) for information about how to 
edit and test your documentation changes.
