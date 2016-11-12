# TaoStore

TODO:
# Encrypt message from client to proxy?
Still need to clear the subtree buckets of the top of the subtree when under multiple partitioning (?)

Test writeback with storage partition: possibly done
Client out of order return problem
Pad the passed messages to always be same length
1024 connections problem
Write more unit and integration tests

Usage:

Performance Disclaimers:
For optimal performance, "warm up" system beforehand by doing a healthy amount of operations before measuring throughput, response times, etc.
Reasons for this mainly involve initializing several data structures on the first few operations, as well as JIT compiling Java byte code into
native code
