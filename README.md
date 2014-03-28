[Repository](https://github.com/pallet/net-rules-crate) &#xb7;
[Issues](https://github.com/pallet/net-rules-crate/issues) &#xb7;
[API docs](http://palletops.com/net-rules-crate/0.8/api) &#xb7;
[Annotated source](http://palletops.com/net-rules-crate/0.8/annotated/uberdoc.html) &#xb7;
[Release Notes](https://github.com/pallet/net-rules-crate/blob/develop/ReleaseNotes.md)

A [pallet](http://palletops.com/) crate to install and configure
 [net-rules](http://nodejs.org).

### Dependency Information

```clj
:dependencies [[com.palletops/net-rules-crate "0.8.0-alpha.3"]]
```

### Releases

<table>
<thead>
  <tr><th>Pallet</th><th>Crate Version</th><th>Repo</th><th>GroupId</th></tr>
</thead>
<tbody>
  <tr>
    <th>0.8.0-RC.7</th>
    <td>0.8.0-alpha.3</td>
    <td>clojars</td>
    <td>com.palletops</td>
    <td><a href='https://github.com/pallet/net-rules-crate/blob/0.8.0-alpha.3/ReleaseNotes.md'>Release Notes</a></td>
    <td><a href='https://github.com/pallet/net-rules-crate/blob/0.8.0-alpha.3/'>Source</a></td>
  </tr>
  <tr>
    <th>0.8.0-RC.4</th>
    <td>0.8.0-alpha.1</td>
    <td>clojars</td>
    <td>com.palletops</td>
    <td><a href='https://github.com/pallet/net-rules-crate/blob/0.8.0-alpha.1/ReleaseNotes.md'>Release Notes</a></td>
    <td><a href='https://github.com/pallet/net-rules-crate/blob/0.8.0-alpha.1/'>Source</a></td>
  </tr>
</tbody>
</table>

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

## License

Copyright (C) 2012, 2013 Hugo Duncan

Distributed under the Eclipse Public License.
