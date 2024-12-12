# sscheck-core developer guide

## Versioning and git branching strategy

Versions use [semantic versioning](https://semver.org/).  
This library is published in a single Maven artifact with group id "es.ucm.fdi.demiourgoi" and artifact id "sscheck-core".  
Maven repos only allow republish of ${version}-SNAPSHOT versions, so:

- Use version ${version}-SNAPSHOT when creating a new version.
- When a version is ready for publish
  - Change version to "${version}" without "-SNAPSHOT" and publish to maven central. 
  - Commit that change as the latest commit for this branch: this freezes the release. I don't use git tags for now as there is no need, and maven central blocks new publishes anyways
  - Create a new branch increasing the patch version, with "-SNAPSHOT" as above. We can always increase the minor or major version later.
