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
LB_DNS?=ec2-52-23-233-176.compute-1.amazonaws.com

# No need to change the following variables
# They are constants of the makefile
USER=$(shell whoami)
SSH_WEBSERVER_USER_HOST = ec2-user@$(EC2_DNS)
SSH_WEBSERVER = ssh -i $(SSH_KEY) $(SSH_WEBSERVER_USER_HOST)
SSH_LB_USER_HOST = ec2-user@$(LB_DNS)
SSH_LB = ssh -i $(SSH_KEY) $(SSH_LB_USER_HOST)
BASE_DIR=/home/ec2-user/project
WS_PORT=8000
LB_PORT=8080

.PHONY: help webserver

assert-ec2: # Confirms command is being run under an EC2 instance
ifneq ($(USER),ec2-user)
	@echo "------- Error: this is to be run on the EC2 instance -------"
	exit 1
endif

assert-dev: # Confirms that the command is running on the development machine (not an EC2)
ifeq ($(USER),ec2-user)
	@echo "------- Error: this is to be run on the EC2 instance -------"
	exit 1
endif

upload-code: assert-dev ## uploads the code to ec2 instance
	@echo "*** Syncing code with ec2 instance"
	@rsync -r -e 'ssh -i $(SSH_KEY)' . $(SSH_WEBSERVER_USER_HOST):~/project

remote-webserver: assert-dev upload-code ## runs webserver on target ec2 instance
	$(SSH_WEBSERVER) make webserver -f project/Makefile

webserver: assert-ec2 ## provision webserver
	@echo "*** Deploying webserver on ec2 instance"
	$(BASE_DIR)/scripts/provision-java7.sh
	$(BASE_DIR)/scripts/config-bit.sh
	$(BASE_DIR)/scripts/kill-running-server.sh $(WS_PORT)
	cd $(BASE_DIR); make run

upload-code-lb: assert-dev ## uploads the code to loadbalancer instance
	@echo "*** Syncing code with ec2 instance"
	@rsync -r -e 'ssh -i $(SSH_KEY)' . $(SSH_LB_USER_HOST):~/project

remote-lb: assert-dev upload-code-lb ## runs load-balancer on target EC2 instance
	@$(SSH_LB) make load-balancer -f project/Makefile

provision-lb: assert-ec2  ## provision loadbalancer
	$(BASE_DIR)/scripts/provision-java7.sh
	$(BASE_DIR)/scripts/kill-running-server.sh $(LB_PORT) 
	$(BASE_DIR)/scripts/provision-aws-sdk.sh

load-balancer: assert-ec2 provision-lb run-lb
	@echo "*** Deploying Load Balancer on ec2 instance"


# Ignore this last part; Just for priting help messages
help: ## Prints this message and exits
	@printf "Subcommands:\n\n"
	@perl -F':.*##\s+' -lanE '$$F[1] and say "\033[36m$$F[0]\033[0m : $$F[1]"' $(MAKEFILE_LIST) \
	| sort \
	| column -s ':' -t


# basic webserver compilation
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


#----------------------------------#
#  basic loadbalancer compilation  #
#----------------------------------#
LB_MAIN_CLASS=$(PACKAGE).load_balancer.LoadBalancer
LB_SDK_DIR=$(HOME)/aws-java-sdk
LB_CP=$(BASE_DIR):$(LB_SDK_DIR)/lib/aws-java-sdk.jar:$(LB_SDK_DIR)/third-party/lib/*

compile-lb: ## compile load balancer
	@echo "*** Compiling project"
	javac -cp $(LB_CP) $(BASE_DIR)/pt/ulisboa/tecnico/cnv/load_balancer/*.java

run-lb: compile-lb ## run load-balancer
	@echo "*** forwarding :80 -> :$(LB_PORT) (in order to run java as regular user)"
	sudo iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-port $(LB_PORT)
	@echo "*** Running load balancer"
	java $(JFLAGS) -cp "$(LB_CP)" $(LB_MAIN_CLASS)

