SHELL := /bin/bash

.SILENT:

UNAME := $(shell uname)
ROOT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))

GRADLE := $(ROOT_DIR)/gradlew

default: help

# https://news.ycombinator.com/item?id=11939200
.PHONY: help
help:	### list main targets
ifeq ($(UNAME), Linux)
	@grep -P '^[a-zA-Z_-_/]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'
else
	@awk -F ':.*###' '$$0 ~ FS {printf "%15s%s\n", $$1 ":", $$2}' \
		$(MAKEFILE_LIST) | grep -v '@awk' | sort
endif

.PHONY: release
release: clean build docs publishLocal	### run all release checks
	echo "PASS all release checks"

.PHONY: clean
clean:	### cleanup the build
	$(GRADLE) clean

.PHONY: build
build:	### build app (this also runs unit tests)
	$(GRADLE) build
	echo "See test report at file://$(ROOT_DIR)/lib/build/reports/tests/test/index.html"

.PHONY: docs
docs:	### generate HTML documentation
	$(GRADLE) dokkaHtml
	echo "See HTML documentation at file://$(ROOT_DIR)/lib/build/dokka/html/index.html"

.PHONY: publishLocal
publishLocal:	### publish to local maven repo
	$(GRADLE) publishToMavenLocal
	ls -al ~/.m2/repository/es/ucm/fdi/demiourgoi/sscheck-core
