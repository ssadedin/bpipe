
# The cleanup statement

### Synopsis
    
      config { ... configuration }

### Availability

      0.9.9.4+

### Behavior

The *config* statement allows configuration of Bpipe from inside a pipeline
script. Please note that some aspects of configuration are already acted
upon by Bpipe before this is read, so not every configuration 
attribute can be modified.

### Examples

**Configure to use different memory for large data vs small**
```groovy 

large_data=true

config {
    executor="torque"
    memory=large_data ? "24g" : "12g"
}
```
