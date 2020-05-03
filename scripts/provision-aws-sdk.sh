#!/bin/bash

# - installs the AWS Java SDK in $HOME/aws-java-sdk
# - renames lib/aws-java-sdk-1.11.774.jar to lib/aws-java-sdk.jar

SDK_DIR=$HOME/aws-java-sdk

# Install AWS Java SDK if not yet installed
if [[ ! -d "/home/ec2-user/aws-java-sdk" ]]; then
    wget http://sdk-for-java.amazonwebservices.com/latest/aws-java-sdk.zip -q -O /tmp/aws-java-sdk.zip
    unzip -q /tmp/aws-java-sdk.zip -d $HOME/
    mv $HOME/aws-java-sdk-* $SDK_DIR

    # rename lib/aws-java-sdk-1.11.774.jar to lib/aws-java-sdk.jar
    (cd $SDK_DIR/lib; mv $(ls -1 | egrep "aws-java-sdk-[1-9.]+jar") aws-java-sdk.jar)
fi

