#!/bin/bash
for i in {1..7}
do
   ./client.sh 132.207.12.245 $i > results$i.txt
done
