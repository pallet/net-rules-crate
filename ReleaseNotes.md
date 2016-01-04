## 0.8.0-beta.1

- Filter duplicate permissions
  When setting permissions in the pallet-ec2 backend, ensure duplicate 
  permissions are removed.

## 0.8.0-alpha.9

- Don't remove group when no compute-service
  When the target does not specify a compute service, do not try removing 
  the network rules.

## 0.8.0-alpha.8

- Correctly reconcile existing, target permissions
  Reconciliation was incorrect when multiple ip ranges were already in place
  for a protocol/port.

- Configure network rules on install

- Add extra precondtions for permit-source and permit-role

## 0.8.0-alpha.7

- Remove security groups on group removal
  When a pallet group is removed, ensure the pallet-ec2 created security 
  group is removed.

- Update to pallet 0.8.4

- Make pallet EC2 security groups exhaustive
  Removes any rules that aren't defined in the net-rules settings.

- Ensure exceptions are propagated
  Exceptions were not being re-thrown correctly.

## 0.8.0-alpha.6

- Ignore duplicate rule exceptions in pallet-ec2

## 0.8.0-alpha.5

- Update pallet-ec2 backend to work with VPC
  Uses security group ids, rather than names, so that it works, in EC@
  classic, default VPC, and non-default VPC.

## 0.8.0-alpha.4

- Add :default-phases to server-spec

- Add implementation for pallet-ec2
  Closes #1

- Add schema for permissions

# 0.8.0-alpha.3

- Use pallet-jclouds 1.7.0-alpha.2
  Use the clj-jclouds functions to access jclouds.

# 0.8.0-alpha.2

- Update for pallet 0.8.0-RC.7

# 0.8.0-alpha.1

- Initial release
