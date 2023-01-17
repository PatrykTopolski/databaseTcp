start java DatabaseNode -tcpport 9001 -connect localhost:9000 -record 2:7

start java DatabaseNode -tcpport 9002 -connect localhost:9001 -record 3:6

start java DatabaseNode -tcpport 9003 -connect localhost:9002 -record 4:5

start java DatabaseNode -tcpport 9004 -connect localhost:9003 -record 5:4

start java DatabaseNode -tcpport 9005 -connect localhost:9004 -record 6:3

start java DatabaseNode -tcpport 9006 -connect localhost:9005 -connect localhost:9000 -record 7:1
