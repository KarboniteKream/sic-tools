#!/bin/bash

gcc -std=c99 -Wall -Wextra -pedantic -lcurses -O3 main.c -o sim
./sim
