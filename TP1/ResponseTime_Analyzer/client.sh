#!/bin/bash

pushd $(dirname $0) > /dev/null
basepath=$(pwd)
popd > /dev/null

cat << EndOfMessage
HELP: 
./client.sh ip_address
	- remote_server_ip: (OPTIONAL) l'addresse ip du serveur distant

EndOfMessage

java -cp "$basepath"/client.jar:"$basepath"/shared.jar -Djava.security.policy="$basepath"/policy ca.polymtl.inf8480.tp1.client.Client $*
