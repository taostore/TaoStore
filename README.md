TaoStore
========

This is an implementation of TaoStore (Cetin et al, S&P 2016) that is meant to be used for both research purposes, as well as for use in actual
real world applications.

***** 

**Basic Usage:**
* If using the default, provided implementation, in order to compile, we suggest bringing the project into a Java IDE such as IntelliJ or Eclipse
* Once in an IDE, to use the provided TaoStore implementation you will need to build three jars
  * A client jar, whose main is found in *TaoClient/TaoClient.java*
  * A proxy jar, whose main is found in *TaoProxy/TaoProxy.java*
  * A server jar, whose main is found in *TaoServer/TaoServer.java*
* These jars can then be either run all locally for a local simulation, or can be run on different 
  machines e.g. client(s) on local machine(s), proxy on trusted machine on network, server(s) run on the cloud
* These jars will rely on several configuration variables, which are seen in *Configuration/TaoConfigs.java*
  * Some of these properties are listed in *Configuration/TaoDefaultConfigs*, and thus can be changed
    * One can modify these by creating a *config.properties* file in the same directory as the jar
  * Those properties not listed in *Configuration/TaoDefaultConfigs.properties* are calculated at runtime and are based
  on user provided properties
  * Note that some of the properties that can be defined in *config.properties* depend on implementation (such as block meta data size)
  and thus should be changed with caution
* The required order of starting the jars: start the server(s). Then start the proxy, which will initialize the tree by writing empty paths to the
  server(s). Depending on how large you made the minimum server size, this can take a varying amount of time. The creation of these paths should 
  be logged to the terminal of the machine running the proxy. When the initialization is complete, you may run the client.
* For optimal performance, "warm up" system beforehand by doing a healthy amount of operations before measuring throughput, response times, etc.
  * Reasons for this mainly involve needing to initialize several data structures, as well as JIT compiling Java byte code into native code
* Has dependency on guava.jar https://github.com/google/guava 
  * Can be downloaded here https://github.com/google/guava/wiki/Release19
  
***** 
  
**Command Line Arguments:**
* For TaoClient, TaoProxy, TaoServer
  * --config_file full_path_to_file
    * The full path to a config file 
    * Default is *config.properties*
* For TaoClient
  * --runType
    * Can be either *interactive* or *load_test*
    * Default is *interactive*
    * An interactive lets the user do manual operations 
    * A load test runs a load test
    * Note that a load test begins after the client does an initial *data_set_size* writes to the proxy to initialize blocks
  * --load_test_type
    * Can either be *synchronous* or *asynchronous*
    * Default is *synchronous*
    * A synchronous load test will run operations that block until the proxy replies to the previous operation
    * An asynchronous load test will run operations that will not block until the reply returns, moving immediately to the next operation 
  * --load_size
    * Specifies the number of operations to be executed in the load test
    * Default is 1000
  * --data_set_size
    * The amount of unique data items to be used in the load test
    * Default is 1000
* For TaoProxy
  * --proxy_type
    * Can be either *synchronous_optimized* or *asynchronous_optimized*
    * Default is *synchronous_optimized*
    * This option either optimizes the proxy for a synchronous load test or an asynchronous load test
    
*****    
 
**Usage in Larger Application:**
* To use in a larger application, set up proxy and server(s) as described in *Basic Usage* and then create your own instance of a TaoClient within
  your application (or use your own implementation of Client, see *Extending For Custom Use*).
  * This client's callable methods can be found in the *TaoClient/Client.java* interface

***** 

**Extending For Custom Use:**
* The code is separated into 3 major components: client, proxy, and server
  * The client and proxy implement an interface, and thus you can use your own custom implementation of either so long as they work together
* This code is written with the idea that developers may have their own uses cases our desired implementations for several parts, and so several
  of the major components are written as interfaces such that developers can customize what they need to.
  * For example, the Proxy can be easily switched out for any other implementation so long as it is still able to communicate with the client and
  server(s) and implements the functionality required by the proxy interface and the TaORAM algorithm.
  * Blocks, buckets, and path are also all able to be switched in and out with custom implementations as desired. The PathCreator interface is the 
  interface for a class that can create a block, bucket, or path and thus depending on what implementation you want, you will just need to create a
  class that implements the PathCreator interface, and returns your custom instances of blocks/buckets/paths.
  * The above is also applicable for message types and the MessageCreator interface
