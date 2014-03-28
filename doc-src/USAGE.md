## Usage

The net-rules crate provides a mechanism to control network port
access.  The implementation varies by provider (by default).
Currently there are implementations for AWS EC2 via jclouds and the
pallet-aws provider.

The net-rules crate provides a `server-spec` function that returns a
server-spec. This server spec will install anything necessary and
configure the network rules..

The `server-spec` provides an easy way of using the crate functions,
and you can use the following crate functions directly if you need to.
The `server-spec` accepts a map of settings as used with `settings`.

The `settings` function provides a plan function that should be called
in the `:settings` phase.  The `:implementation` key can be used to
select a non-default implementation of net-rules.  The only currently
supported implementation is for the jclouds `:aws-ec2` provider.

The `install` function is responsible for installing anything
necessary for the net-rules implementation..

The `permit-group` function is called to permit access to the current
node from a pallet group.

The `permit-role` function is called to permit access to the current
node from all nodes with the specified pallet role .

The `permit-source` function is called to permit access to the current
node from an IP address range (using a CIDR string).
