
## TODOs

* Refactor OSGi providers to remove netty-specific code in a separate netty handler project

* Support handlers which declare they wish to be deleted

* Logging, lol

* Currently using 3 different "future" APIs: OSGi, Netty, and Java futures. Reduce to 2 or 1

* Currently using different event executors. For handlers, use Netty's via the context or provide an event executor group w/ the handler and use that for non-IO.

* Use event admin for event signaling for multiple consumers that may or may not be in the pipeline

* Refactor dynamic handler to parallelize as much of the handler adding code as possible. It's kinda scary right now.
