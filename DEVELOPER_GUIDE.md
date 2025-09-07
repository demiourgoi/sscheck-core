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

This repo uses mostly [semantic versioning](https://semver.org/), but we sometimes add the suffix "-SNAPSHOT" for development versions.  
We publish to https://central.sonatype.com/artifact/io.github.demiourgoi/sscheck-core_2.13  
See one time setup instructions in our shared Google drive doc. __NOTE__ the GPG password for `publishSigned` referred there.

Maven Central repos do not allow republish of the same version of a library. This is to avoid depending on a moving target, that corresponds to different code depending on the day. There is mechanism to use `${version}-SNAPSHOT` versions for development, but I have not been able to make it work, and it is not a great solution anyway for that reason.  
So the __publishing process__ is as follows:

- We use version `${version}-SNAPSHOT` for development, using `publishLocal` or `publishM2` to make the snapshot version available in the same workspace.
- When a version is ready for publish
  - Change version to "${version}" without "-SNAPSHOT" and publish the version as seen below
  - We are not using branches or tags for each versions, we can add that later if needed, but it's complexity with no value for now. We just use the `scala_2.13` branch, with the _invariant_ that the version monotonically increases as we move towards the tip of the branch.


How to actually publish:

```bash
sbt
# leaves it on https://central.sonatype.com/repository/maven-snapshots/io/github/demiourgoi/sscheck-core_2.13/0.5.1-SNAPSHOT/sscheck-core_2.13-0.5.1-SNAPSHOT-javadoc.jar
publishSigned
# only works for non SNAPSHOT
# Appears on https://central.sonatype.com/publishing/deployments
sonaUpload
# Either press "Publish" on https://central.sonatype.com/publishing/deployments or
# use this target. This takes a while and then in https://central.sonatype.com/publishing/deployments appears
# as published, and also in https://central.sonatype.com/artifact/io.github.demiourgoi/sscheck-core_2.13/versions
sonaRelease
```

## VsCode

[Specs2 works with Metals](https://etorreborre.github.io/specs2/guide/5.0.0-RC-11/org.specs2.guide.RunInIDE.html)

Run the task "Metals: Import build" to setup the IDE and check the "OUTPUT" tab below. 

Metals doesn't seem to work on Windows. For Ubuntu install the JDK sources (e.g. with `sudo apt install openjdk-19-source`) and re-run "Metals: Run doctor"

### Cline

Optionally, [Cline](https://docs.cline.bot/) is a free code assistant that can accelerate coding tasks, specially when using the [memory bank pattern](https://docs.cline.bot/prompting/cline-memory-bank). You need to connect to an LLM inference API to use it. Some simple options:

- [Mistral](https://console.mistral.ai) using the "Experiment" that does log your prompts for training the model, which should be fine as this is an open source project anyway. Per [Mistral docs](https://mistral.ai/solutions/coding), Devstral medium is a good option for Cline.
- Gemini using Google AI Studio, getting an API key [here](https://aistudio.google.com/u/1/apikey). Probably also uses the prompts for training. 


## Specs2 docs

You have to use the version of the specs2 user guide corresponding to the dependency on build.sbt, that currently uses version 4.x.x. 
https://etorreborre.github.io/specs2/guide/SPECS2-4.10.0/org.specs2.guide.UserGuide.html it's the latest I found online