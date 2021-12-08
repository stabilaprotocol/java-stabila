# How to deploy java-stabila after modularization

After modularization, java-stabila is launched via shell script instead of typing command: `java -jar FullNode.jar`.

*`java -jar FullNode.jar` still works, but will be deprecated in future*.

## Download

```
git clone git@github.com:stabilaprotocol/java-stabila.git
```

## Compile

Change to project directory and run:
```
./gradlew build
```
java-stabila-1.0.0.zip will be generated in java-stabila/build/distributions after compilation.

## Unzip

Unzip java-stabila-1.0.0.zip
```
cd java-stabila/build/distributions
unzip -o java-stabila-1.0.0.zip
```
After unzip, two directories will be generated in java-stabila: `bin` and `lib`, shell scripts are located in `bin`, jars are located in `lib`.

## Startup

Use the corresponding script to start java-stabila according to the OS type, use `*.bat` on Windows, Linux demo is as below:
```
# default
java-stabila-1.0.0/bin/FullNode

# using config file, there are some demo configs in java-stabila/framework/build/resources
java-stabila-1.0.0/bin/FullNode -c config.conf

# when startup with SR mode，add parameter: -w
java-stabila-1.0.0/bin/FullNode -c config.conf -w
```

## JVM configuration

JVM options can also be specified, located in `bin/java-stabila.vmoptions`:
```
# demo
-XX:+UseConcMarkSweepGC
-XX:+PrintGCDetails
-Xloggc:./gc.log
-XX:+PrintGCDateStamps
-XX:+CMSParallelRemarkEnabled
-XX:ReservedCodeCacheSize=256m
-XX:+CMSScavengeBeforeRemark
```