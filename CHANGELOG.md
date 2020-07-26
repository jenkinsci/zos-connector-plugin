# Change log

## [2.3.4]
### Changed
- Use README as doc source

## [2.3.3]
### Added
- FTP active mode support (kudo's to [Pavel Kondratiev](email:kondratiev@iba.by))

## [2.3.2]
### Fixed
- NPE if FTP failed to list files (closed socket, etc.)

## [2.3.1]
### Fixed
- Regression from [2.3.0]: `JESINTERFACELEVEL=2` had problems with job listing and didn't check for RC
[JENKINS-57549](https://issues.jenkins-ci.org/browse/JENKINS-57549)

## [2.3.0]
### Fixed
- `JESINTERFACELEVEL=1` inconsistencies [JENKINS-56976](https://issues.jenkins-ci.org/browse/JENKINS-56976)
- Too many reconnects (regression from [2.2.0])
### Added
- Handle RC in `JESINTERFACELEVEL=1` (but you need to check that correct level setting in Jenkins job config)

## [2.2.0]
### Fixed
- Logout of the FTP server as soon as single request is performed.  
This will result in increased number of connect → login → logout →
disconnect sequences, but should also make plugin more stable.

## [2.1.0]
### Fixed
- Expand environment variables once again before job submission
[JENKINS-55609](https://issues.jenkins-ci.org/browse/JENKINS-55609)

## [2.0.1]
### Fixed
- False positive error message if not waiting on job completion
[JENKINS-54574](https://issues.jenkins-ci.org/browse/JENKINS-54574)

## [2.0.0]
### Changed
- Credentials instead of username-password pair
- Text file with JCL code instead of plaintext field (if you need to track SCLM in the same job - consider using Multiple SCMs plugin)

## [1.2.6.1]
### Added
- Option to print JES job log

## [1.2.6]
### Changed
- Update failure logic for slow JES initiators

## [1.2.5]
### Added
- Pipeline support (kudo's to [Robert Obuch)](https://wiki.jenkins.io/display/~robert_obuch))
[JENKINS-44974](https://issues.jenkins-ci.org/browse/JENKINS-44974)
### Changed
- Use Jenkins 2+

## [1.2.4.1]
### Added
- Notification about incompatibility since [1.2.4].

## [1.2.4]
### Added
- Provide different messages into the build log based on the job completion code
[JENKINS-31837](https://issues.jenkins-ci.org/browse/JENKINS-31837)

## [1.2.3]
### Changed
- `JESINTERFACELEVEL=1` output changes and configuration corrections.

## [1.2.2]
### Added
- `JESINTERFACELEVEL=1` support.

## [1.2.1]
- Dummy release

## [1.2.0.2]
### Added
- Added initial wait before listing jobs in JES
[JENKINS-31757](https://issues.jenkins-ci.org/browse/JENKINS-31757)

## [1.2.0.1]
### Added
- New error message if job is not listed while waiting for its execution to end (possible problem with `JESINTERFACELEVEL=1`).

## [1.2.0]
### Added
- Added JobName to output log file name. 
### Changed
- Changed log file naming convention.

## [1.1.1]
### Added
- Add environment variables expansion for "Submit z/OS job" build step. 
- Added loggers.

## [1.1.0]
### Added
- MaxCC parameter to 'Submit z/OS job' build step
[JENKINS-29214](https://issues.jenkins-ci.org/browse/JENKINS-29214)

## [1.0.4]
### Added
- Support for 'RC unknown'.

## [1.0.3]
### Fixed
- Fix for [JENKINS-29173](https://issues.jenkins-ci.org/browse/JENKINS-29173).
