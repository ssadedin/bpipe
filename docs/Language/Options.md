## The Options statement

### Synopsis

```    

  options {
     <option name> <description> [, required:<true|false>] [, args: <integer>]
  } 
```
    

### Availability

0.9.9.8

### Behavior

Parses the specified options from the command line arguments given *after* the pipeline in the 
Bpipe command. The actual pipeline then runs on the residual arguments that are not parsed as 
options.

The option specification uses the format of the Groovy CliBuilder class. The builder is set 
as a delegate so that the options can be specified directly using the abbreviated syntax, without
explicitly referencing the CliBuilder class.

Many extended options can be specified using the full configuration available via 
[CliBuilder](http://docs.groovy-lang.org/2.4.7/html/gapi/groovy/util/CliBuilder.html) or further
examples [here](https://mrhaki.blogspot.com/2009/09/groovy-goodness-parsing-commandline.html).

After the `options` statement has executed, the options are available via an implicit `opts` 
variable with properties named after the options.

If a required option is not provided, a usage message with a description of the options will
be printed.

### Examples

**Require foo parameter to run the pipeline**

```
options {
    foo 'The foo value to use', args: 1, required: true 
    bar 'The bar value to use', args: 1, required: false
}

hello = {
   exec """
     echo "The foo value chosen was $opts.foo"
   """
}

run { hello }
```

To run this:

```
bpipe run test.groovy -foo 1 -bar 2 
```

