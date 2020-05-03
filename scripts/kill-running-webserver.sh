#!/bin/sh
WEBSERVER_PORT=8000
kill $(sudo ss -tupln | grep $WEBSERVER_PORT |egrep "pid=[0-9]*" -o | egrep "[^pid=][0-9]*" -o) || true
