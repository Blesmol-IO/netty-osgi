
## TODO Tests

* Target tests that ensure non-targeted components are not applied to components assumed to have targets, such as Netty server and client providers
 
* Event executor group

* Refactor tests: Some use static variables / @{Before|After}Class; some use instance @Before/@After.

* A test that ensures the dynamic handler adds all handlers via the event executor

* Performance tests! Memory consumption and speed

* Closed / deregistered tests to ensure things are cleaned up