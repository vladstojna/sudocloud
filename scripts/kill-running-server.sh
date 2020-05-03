#!/bin/bash
#
# Kills the process running on specified port

if [ ! $# -eq 1 ]; then
    echo "Usage: $0 [port]"
    exit 1
fi

sudo kill $(sudo ss -tupln | grep $1 |egrep "pid=[0-9]*" -o | egrep "[^pid=][0-9]*" -o) || true
