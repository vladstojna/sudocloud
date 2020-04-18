#!/bin/sh

# Install BIT if not yet installed
if [[ ! -d "/home/ec2-user/BIT" ]]; then
    wget http://grupos.ist.utl.pt/meic-cnv/labs/labs-bit/BIT.zip -O /tmp/BIT.zip
    unzip /tmp/BIT.zip -d ~ec2-user/
fi

