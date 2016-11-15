TaoStore
========

**Usage:**
* All configurations can be found in *Configuration/TaoConfigs.java*. In this file you can set the IPs/ports for the proxy and servers, the writeback threshold, etc.
* Three jars will need to be built, one for clients, one for the proxy, and one for servers. 
  * The client jar takes in no arguments
  * The proxy jar takes in the total size of the system in bytes
  * The server jar takes in the total size of the system in bytes
* For optimal performance, "warm up" system beforehand by doing a healthy amount of operations before measuring throughput, response times, etc.
  * Reasons for this mainly involve needing to initialize several data structures, as well as JIT compiling Java byte code into native code
* Has dependency on guava.jar https://github.com/google/guava 
  * Can be downloaded here https://github.com/google/guava/wiki/Release19
