# Using Custom Classes by Extending the Bpipe ClassPath

One of the great things about Bpipe is that under the covers you have
access to the whole JVM ecosystem to add libraries for use in your
pipeline scripts. To do that, though, you need to be able to add those
libraries into the class path. There are several ways to add your own
classes or third party libraries in.


## Add an extra-lib Jar File

Make a folder called "bpipes" in your home directory and put a jar
file in there called "extra-lib.jar". This was just an old workaround so it is
kind of clunky but it does still work.

## Add Libraries in the bpipe.config file

Now there is a more refined way: create a bpipe.config file next to your
pipeline script, or in the directory you are running the pipeline in. You can
then add in there a "libs" setting:

```
libs="/path/to/a/file.jar:/path/to/another/file.jar"
```

These jars will all get included at runtime.

## Use Groovy Grab / Grape

There's a third way which is neat if you want to distribute the pipeline
script, but it doesn't quite work now out of the box. If you copy the file
"ivy-2.4.0.jar " out of a normal Groovy installation into your Bpipe local-lib
folder then you can use the native Groovy "Grape" mechanism to import libraries
inside your pipeline script - eg:

```groovy
@Grapes(
          @Grab(group='com.xlson.groovycsv', module='groovycsv', version='1.1')
       )       
import com.xlson.groovycsv.*

def csv = CsvParser.parse(...)


```


