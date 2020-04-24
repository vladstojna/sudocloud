#!/bin/sh

# This is a quick and dirty provisiong script to send the current
# code to the server for testing

# Configure these environment variables:
#   AWS_SSH_KEY - Your ssh key to connect you the EC2 instance
#
export BIT_TOOL=SolverStatistics # instrumentation tool to test

export TARGET_PROJECT_PATH=~/project

export TAG="[  INFO  ]"

export BIT_PATH=~/BIT

export WEBSERVER=$TARGET_PROJECT_PATH/webserver
export WEBSERVER_PORT=8000
export CLASSPATH=$BIT_PATH:$BIT_PATH/samples:$WEBSERVER:$TARGET_PROJECT_PATH/instrumentation

export WEBSERVER_CODE=$WEBSERVER/pt/ulisboa/tecnico/cnv/server
export SOLVERS_CODE=$WEBSERVER/pt/ulisboa/tecnico/cnv/solver
export INSTRUMENTED_CODE=$SOLVERS_CODE

# set -e

source $TARGET_PROJECT_PATH/scripts/provision-java7.sh
source $TARGET_PROJECT_PATH/scripts/config-bit.sh

#	First we compile the original webserver
echo $TAG compiling webserver
javac $TARGET_PROJECT_PATH/webserver/pt/ulisboa/tecnico/cnv/server/WebServer.java

#	Instrumentation of the webserver
echo $TAG TODO instrument solvers
echo $TAG compiling instrumentation tools
javac $TARGET_PROJECT_PATH/instrumentation/*.java 

#	Instrumentation with the selected tool; The code is instrumented inplace
echo $TAG instrumenting with $BIT_TOOL tool
java $BIT_TOOL $INSTRUMENTED_CODE $INSTRUMENTED_CODE

echo $TAG killing previous servers running on port $WEBSERVER_PORT
kill $(sudo ss -tupln | grep $WEBSERVER_PORT |egrep "pid=[0-9]*" -o | egrep "[^pid=][0-9]*" -o) || true
sleep 1

echo $TAG running webserver
java $WEBSERVER_CP pt.ulisboa.tecnico.cnv.server.WebServer && read


################################################
## Start of commands to be executed on server ## 
#set -e
#
#echo "downloading AWS java sdk"
#cd /tmp
#wget http://sdk-for-java.amazonwebservices.com/latest/aws-java-sdk.zip -q -O aws-java-sdk.zip
#unzip aws-java-sdk.zip -d sdk
#cd sdk
#mv $(ls -1) ~/aws-java-sdk
#pwd##
#
#echo "adding to classpath"
#export CLASSPATH=$CLASSPATH:~/sdk/lib/*.jar:~/sdk/third-party/lib/*:.
#
##  End of commands to be executed on server  ##
################################################


