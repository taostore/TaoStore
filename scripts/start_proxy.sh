#!/usr/bin/env sh

cd ..

java -classpath ./out/production/TaoStore:./libs/guava-19.0.jar:./libs/commons-math3-3.6.1.jar:./libs/junit-4.11.jar TaoProxy.TaoProxy
