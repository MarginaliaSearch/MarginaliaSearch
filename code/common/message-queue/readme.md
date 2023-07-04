# Message Queue

Implements resilient message queueing for the application,
as well as a finite state machine library backed by the
message queue that enables long-running tasks that outlive
the execution lifespan of the involved processes. 

![Message States](msgstate.svg)