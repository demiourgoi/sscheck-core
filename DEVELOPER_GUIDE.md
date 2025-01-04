# Developer guide

## Basic targets

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
publishLocal

# ver tb https://github.com/demiourgoi/flink-check/blob/master/ci/run_all_tests.sh
sbt -no-colors 'publishLocal'

help
exit

# non interactive: slower
sbt -no-colors compile
```