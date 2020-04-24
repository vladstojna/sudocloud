# Virtualization Makefile                                           #
#                                                                   #
# This file is responsible for the various deployment and           #
# maintenance tasks of the virtual machines.                        #
#                                                                   #
### Prerequisite: configuring ~/.ssh/config #########################
#                                                                   #
# Prior to using it, you need to configure you ~/.ssh/config        #
# like the example                                                  #
#                                                                   #
# Host webserver                                                    #
# 	HostName ec2-3-80-58-62.compute-1.amazonaws.com             #
# 	User ec2-user                                               #
#	IdentityFile ~/.ssh/uservirtualization.pem                  #
#                                                                   #
### Usage: ##########################################################
#
# $ make help           For listing all the available commands
# $ make [command]      For running the desired command
#

# Change these variables via environment variable overrides
SSH_KEY?=~/.ssh/uservirtualization.pem
EC2_DNS?=ec2-3-80-58-62.compute-1.amazonaws.com

# No need to change the following variables
# They are constants of the makefile
USER=$(shell whoami)
SSH_USER_HOST =  ec2-user@$(EC2_DNS)
SSH = ssh -i $(SSH_KEY) $(SSH_USER_HOST)
TARGET_PROJECT_PATH = /home/ec2-user/project

.PHONY: help webserver

assert-ec2: # Confirms command is being run under an EC2 instance
ifeq ($(USER),ec2-user)
	@echo "------- Error: this is to be run on the EC2 instance -------"
	exit 1
endif

assert-dev: # Confirms that the command is running on the development machine (not an EC2)
ifneq ($(USER),ec2-user)
	@echo "------- Error: this is to be run on the EC2 instance -------"
	exit 1
endif

webserver: ## provision webserver
ifneq ($(USER),ec2-user) # if running on dev enviornment
	rsync -r -e 'ssh -i $(SSH_KEY)' . $(SSH_USER_HOST):~/project
	$(SSH) make webserver -f project/Makefile

else # if running on ec2
# 	From this point on the code is only running on EC2
	@echo $(TAG) "running code on EC2 instance"
	$(TARGET_PROJECT_PATH)/scripts/provision-webserver.sh
endif

webserver-rnl: ## provision webserver on RNL machine
	cp -r . ~/project
	~/project/scripts/provision-webserver.sh


# Ignore this last part; Just for priting help messages
help: ## Prints this message and exits
	@printf "Subcommands:\n\n"
	@perl -F':.*##\s+' -lanE '$$F[1] and say "\033[36m$$F[0]\033[0m : $$F[1]"' $(MAKEFILE_LIST) \
		| sort \
		| column -s ':' -t

# basic compilation

BASEDIR=$(shell pwd)
BIT_BASEDIR=$(BASEDIR)/BIT
PROJECT_BASEDIR=$(BASEDIR)/pt/ulisboa/tecnico/cnv

MAIN_CLASS="pt.ulisboa.tecnico.cnv.server.WebServer"
JAVA_OPTIONS=-XX:-UseSplitVerifier

compile:
	javac \
		$(BIT_BASEDIR)/highBIT/*.java \
		$(BIT_BASEDIR)/lowBIT/*.java \
		$(PROJECT_BASEDIR)/instrumentation/*.java \
		$(PROJECT_BASEDIR)/server/*.java \
		$(PROJECT_BASEDIR)/solver/*.java

clean:
	rm $(BIT_BASEDIR)/highBIT/*.class \
		$(BIT_BASEDIR)/lowBIT/*.class \
		$(PROJECT_BASEDIR)/instrumentation/*.class \
		$(PROJECT_BASEDIR)/server/*.class \
		$(PROJECT_BASEDIR)/solver/*.class

run: compile
	java $(JAVA_OPTIONS) -cp "$(BASEDIR)" $(MAIN_CLASS)
