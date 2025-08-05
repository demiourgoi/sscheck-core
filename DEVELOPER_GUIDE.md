# Developer guide

## How to build the code

```bash
# Launch SBT shell
sbt

# list targets
tasks
# reload sbt config
reload

clean
compile
test

# run linter: see CI target on .github\workflows\sscheck_core.yml
scalafixEnable
scalafixAll --check

publishLocal
# for making it available to gradle
publishM2

# see also https://github.com/demiourgoi/flink-check/blob/master/ci/run_all_tests.sh
sbt -no-colors 'publishLocal'

help
exit

# non interactive: slower
sbt -no-colors compile
```

## Maven artifact publishing

Maven publishing is not setup yet.  
Use `sbt -no-colors 'publishLocal'` to publish to the local maven repo. 

## Versioning and git branching strategy

Versions use [semantic versioning](https://semver.org/).  
This library is published in a single Maven artifact with group id "es.ucm.fdi.demiourgoi" and artifact id "sscheck-core".  
Maven repos only allow republish of ${version}-SNAPSHOT versions, so:

- Use version ${version}-SNAPSHOT when creating a new version.
- When a version is ready for publish
  - Change version to "${version}" without "-SNAPSHOT" and publish to maven central. 
  - Commit that change as the latest commit for this branch: this freezes the release. I don't use git tags for now as there is no need, and maven central blocks new publishes anyways
  - Create a new branch increasing the patch version, with "-SNAPSHOT" as above. We can always increase the minor or major version later.
  - Change the default branch in Github settings
  
## VsCode

[Specs2 works with Metals](https://etorreborre.github.io/specs2/guide/5.0.0-RC-11/org.specs2.guide.RunInIDE.html)

Run the task "Metals: Import build" to setup the IDE and check the "OUTPUT" tab below. 

Metals doesn't seem to work on Windows. For Ubuntu install the JDK sources (e.g. with `sudo apt install openjdk-19-source`) and re-run "Metals: Run doctor"

### Cline

Optionally, [Cline](https://docs.cline.bot/) is a free code assistant that can accelerate coding tasks, specially when using the [memory bank pattern](https://docs.cline.bot/prompting/cline-memory-bank). You need to connect to an LLM inference API to use it.  
A simple option is [Mistral](https://console.mistral.ai) using the "Experiment" that does log your prompts for training the model, which should be fine as this is an open source project anyway. Per [Mistral docs](https://mistral.ai/solutions/coding), Devstral medium is a good option for Cline.


## Specs2 docs

You have to use the version of the specs2 user guide corresponding to the dependency on build.sbt, that currently uses version 4.x.x. 
https://etorreborre.github.io/specs2/guide/SPECS2-4.10.0/org.specs2.guide.UserGuide.html it's the latest I found online