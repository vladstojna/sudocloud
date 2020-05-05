sudocloud
=========

Setup
-----

Before doing anything else you should make sure you satisfy all of
these requirements:
  - developing on a linux environment
  - installed: make, rsync (on both your computer and ec2)

For deploying the loadbalancer you will also need to setup you AWS
credetials file. You just need to `cp aws_credentials.template
aws_credentials` and edit `aws_credentials` to fill in with your
credentials.


Deploying
---------

The deployment is make though makefiles (linux's build
system). `Makefile` is the file with these instructions.

### Deploying locally

```bash
make run
```

### Deploying remotely

You just need to from the root of the project:

```bash
# Replace this your ssh keypair
export SSH_KEY=~/.ssh/uservirtualization.pem

# deploying webserver (replace with you EC2_DNS)
make remote-webserver EC2_DNS=ec2-52-201-255-184.compute-1.amazonaws.com


# deploying loadbalancer (replace with you LB_DNS)
make remote-lb LB_DNS=ec2-52-23-233-176.compute-1.amazonaws.com

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

