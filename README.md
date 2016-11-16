TaoStore
========

**Basic Usage:**
* In order to compile, we suggest bringing the project into a Java IDE such as IntelliJ or Eclipse
* Once in an IDE, to use the provided TaoStore implementation you will need to build three jars
  * A client jar, whose main is found in *TaoClient/TaoClient.java*
  * A proxy jar, whose main is found in *TaoProxy/TaoProxy.java*
  * A server jar, whose main is found in *TaoServer/TaoServer.java*
* These jars will rely on several configuration variables, which are seen in *Configuration/TaoConfigs.java*
  * Some of these properties are listed in *Configuration/TaoDefaultConfigs*, and thus can be changed
    * One can modify these by creating a *config.properties* file in the same directory as the jar
  * Those properties not listed in *Configuration/TaoDefaultConfigs.properties* are calculated at runtime and are based
  on user provided properties
  * Note that some of the properties that can be defined in *config.properties* depend on implementation (such as block meta data size)
  and thus should be changed with caution
* For optimal performance, "warm up" system beforehand by doing a healthy amount of operations before measuring throughput, response times, etc.
  * Reasons for this mainly involve needing to initialize several data structures, as well as JIT compiling Java byte code into native code
  
  
**Additional Command Line Arguments:**
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
  
    
