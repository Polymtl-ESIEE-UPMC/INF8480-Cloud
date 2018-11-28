#!/bin/bash
for i in {1..30}
do
    wget -qO ./results/index$i.html $1 &
done
wait