# Overview
_go-java - a simple java library that has some go like features_

If you like the go concurrency features of _routines_ and _channels_, then this library gives you a similar experience for Java code.

## Routines
Java does not have anything that is equivalent to a `goroutine`, the nearest match is to use a `Thread`. 

A `goroutine` is a lightweight thread managed by the Go runtime.

Every Java thread requires an operating system thread and a large stack space, so results in a larger memory footprint. 

So although there is a `go` routine, it's all but in name, for now.

## Channels
When data needs to be shared between routines, a `Channel` will act as a pipe and guarantee the synchronous exchange of data. 

There are two different types of `Channel`:  _buffered_ and _unbuffered_ (see [go spec](https://golang.org/ref/spec#Channel_types)).
Put simply, an unbuffered channel supports _synchronous_ communication while a buffered channel is for _asynchronous_ communication. 

When declaring a `Channel`, the type of data shared is specified using the generic type; there are two factory 
methods to support the creation of either type of `Channel`.
 
To create an unbuffered channel of strings:
```java
Channel<String> unbuffered = channel();
```
Alternatively, to create a buffered channel of strings:
```java
Channel<String> buffered = channel(10);
```
To send a value into a channel use `send(T value)` (equivalent `unbuffered <- "message"` in go):
```java
Channel<String> unbuffered = channel();
unbuffered.send("message");
```
The `send` call will block until either a `receive` call is made or `close` is called on the channel. 

To receive the above string from the channel call `receive` (equivalent to `message := <-unbuffered` in go):
```java 
String message = unbuffered.receive();
assertThat(message, is("message"))
```
The call to `receive` will block until `send` is called, or the channel is closed, in which case a `null` value is returned.

In the above code examples, it's expected `send` and `receive` will be run in a separate `Routine` (`Thread`), that share the `Channel`, for example: 
```
final Channel<String> unbuffered = channel();
go(() -> { unbuffered.send("message"); });
go(() -> { assertThat(unbuffered.receive(), is("message")); });
```

To close `Channel` call `close`:
```java
unbuffered.close();
```
Only the sender should close a `Channel`, never the receiver. 

Sending on a closed `Channel` will cause a runtime exception, as will calling `close` on an already closed `Channel`. 
As noted, any blocked calls to `send` or `recieve` will unblock, with call to `recieve` returning a null value.

Channels behave differently depending on whether buffered or unbuffered, so lets look at that next.

### Unbuffered Channels
An unbuffered channel has zero capacity because the value is sent directly from the sender to the receiver. Both the 
sender and receiver therefore need to be ready before the send/receive operation can complete - if one is not ready, 
the other will block until it is. It's very similar to a `SynchronousQueue`. 

### Buffered Channels
A buffered channel has capacity to hold one or more values before they are received. Routines don't need to be ready at 
the same time to perform sends and receives. Blocking can still occur - a send will block if the buffer is full, and 
receive will block if the buffer is empty. It's essentially a `BlockingQueue`

 



