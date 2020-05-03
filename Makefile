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
BASE_DIR=/home/ec2-user/project

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

upload-code: ## uploads the code to ec2 instance
	rsync -r -e 'ssh -i $(SSH_KEY)' . $(SSH_USER_HOST):~/project

webserver: ## provision webserver
ifneq ($(USER),ec2-user) # if running on dev enviornment
	rsync -r -e 'ssh -i $(SSH_KEY)' . $(SSH_USER_HOST):~/project
	$(SSH) make webserver -f project/Makefile

else # if running on ec2
# 	From this point on the code is only running on EC2
	@echo $(TAG) "running code on EC2 instance"
	$(BASE_DIR)/scripts/provision-java7.sh
	$(BASE_DIR)/scripts/config-bit.sh
	$(BASE_DIR)/scripts/kill-running-webserver.sh
	cd $(BASE_DIR); make run
endif


# Ignore this last part; Just for priting help messages
help: ## Prints this message and exits
	@printf "Subcommands:\n\n"
	@perl -F':.*##\s+' -lanE '$$F[1] and say "\033[36m$$F[0]\033[0m : $$F[1]"' $(MAKEFILE_LIST) \
	| sort \
	| column -s ':' -t

#basic compilation

JC = javac
JFLAGS=-XX:-UseSplitVerifier

BASEDIR=$(shell pwd)
BIT_BASEDIR=$(BASEDIR)/BIT
PROJECT_DIR=$(BASEDIR)/pt/ulisboa/tecnico/cnv
PACKAGE=pt.ulisboa.tecnico.cnv

INST_CLASS=$(PACKAGE).instrumentation.SolverStatistics
MAIN_CLASS=$(PACKAGE).server.WebServer

compile: ## compile project
	@echo "*** Compiling project"
	javac $(BIT_BASEDIR)/highBIT/*.java \
		$(BIT_BASEDIR)/lowBIT/*.java \
		$(PROJECT_DIR)/instrumentation/*.java \
		$(PROJECT_DIR)/server/*.java \
		$(PROJECT_DIR)/solver/*.java

clean: ## clean project (generated class files)
	@echo "Cleaning project..."
	rm -f $(BIT_BASEDIR)/highBIT/*.class \
		$(BIT_BASEDIR)/lowBIT/*.class \
		$(PROJECT_DIR)/instrumentation/*.class \
		$(PROJECT_DIR)/server/*.class \
		$(PROJECT_DIR)/solver/*.class

instrument: compile ## instrument solvers
	@echo "*** Instrumenting solvers"
	java $(JFLAGS) -cp "$(BASEDIR)" $(INST_CLASS) \
		$(PROJECT_DIR)/solver $(PROJECT_DIR)/solver

run: instrument ## run web server with instrumented solvers
	@echo "*** Running web server"
	java $(JFLAGS) -cp "$(BASEDIR)" $(MAIN_CLASS)

run-raw: compile ## run web server without instrumented solvers
	@echo "*** Running web server without instrumentation"
	java $(JFLAGS) -cp "$(BASEDIR)" $(MAIN_CLASS)
