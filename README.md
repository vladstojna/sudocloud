sudocloud
=========

Requirements
------------

Before doing anything else you should make sure you satisfy all of
these requirements:
  - developing on a linux environment
  - installed: make, rsync (on both your computer and ec2)


Deploying
---------

The deployment is make though makefiles (linux's build
system). `Makefile` is the file with these instructions.

You just need to from the root of the project:

```bash
# Replace this your ec2 instance and ssh keypair
export EC2_DNS=ec2-52-201-255-184.compute-1.amazonaws.com
export SSH_KEY=~/.ssh/uservirtualization.pem

# for deploying webserver
make webserver

# for listing all other available commands
make help

```


Final deployment
----------------

Here is a checklist with all the stuff we need to do when doing the
final deployment:

- [ ] Configure security groups

- [ ] Deploy LoadBalancer
  - [ ] AWS credentials in ~/.aws folder
  - [ ] can reach MSS
  - [ ] is reachable from internet
  - [ ] forwards requests
  - [ ] create AMI with this setup 

- [ ] Deploy AutoScaler
  - [ ] AWS credentials in ~/.aws folder
  - [ ] AWS SDK installed
  - [ ] can create new instances
  - [ ] can kill instances
  - [ ] can access cloudwatch
  - [ ] create AMI with this setup 
  
- [ ] Deploy solver webserver
  - [ ] classpath includes BIT
  - [ ] instrumented code
  - [ ] can reach MSS
  - [ ] auto-run on startup
  - [ ] create AMI with this setup 

