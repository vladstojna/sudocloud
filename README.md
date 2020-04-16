# sudocloud

## Final deployment

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
