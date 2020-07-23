# zos-connector-plugin
Plugin for connection of Jenkins CI to IBM zOS including integration of IBM SCLM as SCM.

[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/zos-connector-plugin/master)](https://ci.jenkins.io/job/Plugins/job/zos-connector-plugin/job/master/)

| Plugin Information                                                                                           |
|--------------------------------------------------------------------------------------------------------------|
| View IBM z/OS Connector [on the plugin site](https://plugins.jenkins.io/zos-connector) for more information. |

Older versions of this plugin may not be safe to use. Please review the
following warnings before using an older version:

-   [Password stored in plain text](https://jenkins.io/security/advisory/2018-06-25/#SECURITY-950)

## About

This plugin provides its functions via FTP connection to IBM z/OS LPAR.
You can configure your SCLM project on z/OS and then check for the
changes via Jenkins.

Features include:
- Submission of user JCL job (with optional log collected upon finish)
- Introduction of SCLM as SCM for your projects allowing to checkout SCLM changes
  - The ability to build SCLM projects currently can be performed **only** via 'Submit zOS Job' build action

## Version differences
Kindly back up your JCL texts before upgrading to version [2.0.0].
The [2.0.0] release uses text files instead of plaintext input, so old jobs will become obsolete.

## Configuration
### `JESINTERFACELEVEL` differences
Job Name **must** be configured accordingly to your FTP server
configuration:
- If `JESINTERFACELEVEL=1` is configured, **only** a job named `USERIDx`
(`USERID` - your z/OS user ID, `x` - strictly 1 character) can be
processed correctly (when you are waiting for the job to end).
- If `JESINTERFACELEVEL=2` is configured, no additional considerations are required.
