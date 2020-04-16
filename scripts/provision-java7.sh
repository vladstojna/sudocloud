#!/bin/sh
JAVA_VERSION=$(java -version  2>&1 >/dev/null | grep "1.7.0_251" -o)

if [[ "$JAVA_VERSION" == "1.7.0_251" ]]; then
    # install java7 version
    sudo yum install java-1.7.0-openjdk-devel

    # TODO the following might not be running because the Makefile
    # that calls this runs this in a subshell
    # setup env variables accoring to java-config-vm-rnl.sh
    export JAVA_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
    export JAVA_ROOT=/usr/lib/jvm/java-7-openjdk-amd64/
    export JDK_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
    export JRE_HOME=/usr/lib/jvm/java-7-openjdk-amd64/jre
    export PATH=/usr/lib/jvm/java-7-openjdk-amd64/bin/:$PATH
    export SDK_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
    export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS

fi

