# Project 1

## Project Instructions

The goal is to design and mplement a simple **application layer protocol over** <u>**UDP**</u> to facilitate High Availability Cluster (HAC). HAC has a set of mechanisms to detect failovers, network/node failure etc. in order to re-route the traffic to the available systems. In this project your task is to design and implement a protocol to maintain the up-node (alive or dead) information throughout the cluster. Additionally, each node will have a complete up-to-date map of the file listing of a designated "/home" directory for all the other nodes of the cluster.

Your designed protocol should perform the following functions:

a. To detect node failure periodically($^**$also the Server failure in case of Client-Server mode)
b. To be able to detect when the failed node comes back to life
c. To display the up-to-date file listing of "/home/" directory of all the live nodes

## Our Implementation

If you would like to read about our implementation specifically, you can head over to the [project document](https://github.com/bjaxqq/CSC340/blob/main/Project%201/README.md) that we had to write for this project.

## Group Members

This project was completed by:

- Brooks Jackson (me)
- [Aiden Armellino](https://github.com/AJArmy60)
- [Connor Ryan](https://github.com/ConnorRyan313)
