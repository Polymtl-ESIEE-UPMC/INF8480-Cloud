#!/bin/bash

pushd $(dirname $0) > /dev/null
basepath=$(pwd)
popd > /dev/null

java -cp "$basepath"/client.jar:"$basepath"/shared.jar -Djava.security.policy="$basepath"/policy client.Client $*
