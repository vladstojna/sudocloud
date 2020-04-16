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
export USER=$(shell whoami)
export SSH_KEY?=~/.ssh/uservirtualization.pem
export EC2_DNS?=ec2-3-80-58-62.compute-1.amazonaws.com
export SSH_USER_HOST =  ec2-user@$(EC2_DNS)
export SSH = ssh -i $(SSH_KEY) $(SSH_USER_HOST)
export TARGET_PROJECT_PATH = /home/ec2-user/project

export TAG = [  INFO  ]

WEBSERVER_PORT = 8000

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


webserver-update-code: ## Copies your current code to webserver
	rsync -r . webserver:$(TARGET_PROJECT_PATH)

webserver: ## provision webserver
ifneq ($(USER),ec2-user) # if running on dev enviornment
	rsync -r -e 'ssh -i $(SSH_KEY)' . $(SSH_USER_HOST):~/project
	$(SSH) make webserver -f project/Makefile

else # if running on ec2
	@echo $(TAG) "running code on EC2 instance"	
	@$(TARGET_PROJECT_PATH)/scripts/provision-java7.sh

	@echo $(TAG) compiling webserver
	javac -cp $(TARGET_PROJECT_PATH)/webserver $(TARGET_PROJECT_PATH)/webserver/pt/ulisboa/tecnico/cnv/server/WebServer.java

	@echo $(TAG) TODO instrument solvers


	@echo $(TAG) killing previous servers running on port $(WEBSERVER_PORT)
	@kill `sudo ss -tupln | grep $(WEBSERVER_PORT) |egrep "pid=[0-9]*" -o | egrep "[^pid=][0-9]*" -o` || true
	@sleep 1

	@echo $(TAG) running webserver
	java -cp $(TARGET_PROJECT_PATH)/webserver pt.ulisboa.tecnico.cnv.server.WebServer && read

endif

# Ignore this last part; Just for priting help messages
help: ## Prints this message and exits
	@printf "Subcommands:\n\n"
	@perl -F':.*##\s+' -lanE '$$F[1] and say "\033[36m$$F[0]\033[0m : $$F[1]"' $(MAKEFILE_LIST) \
		| sort \
		| column -s ':' -t
