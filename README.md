# Overview
_go-java - a simple java library that has some go like features and advice for high-throughput concurrent applications_

If you like the go concurrency features of _routines_ and _channels_, then this library gives you a similar experience for Java code ...  with some caveats that will be discussed.


## Summary
To get anything comparable to a `goroutine` in Java we look at an alternative OpenJDK called [Project Loom](https://wiki.openjdk.java.net/display/loom/Main) that provides 'virtual threads', a new implementation of `Thread` that differs in memory footprint and scheduling.

A `Channel` can be implemented using the existing core Java API - an `ExecutorService`, `SynchronousQueue` and `ArrayBlockingQueue`. 


## Routines
Java does not have anything that is equivalent to a `goroutine`, the nearest match is a `Thread`, unlike Java threads though, you can run many more goroutines on a typical system, they will have a lower memory footprint, and will perform much better. 

### Java Threads
So whats the problem with Threads? In a nutshell, they are implemented in the JDK as trivial wrappers around operating system (OS) threads, which introduces the following problems ...

#### OS thread limit
If you try and create too many OS threads you'll get:
```java
[error] (run-main-0) java.lang.OutOfMemoryError: unable to create native thread
```
On my macbook I could create 2048 threads before "running out of memory", this is actually linked to the `kern.num_taskthreads` setting of 2048, so problem here was not so much to do with memory, but the number of threads the OS supports for my user. All platforms will impost limits though.  

#### Large thread stack size 
OS threads have a high memory footprint because each thread has a fixed (stack) size - the (64-bit) default is 1MB, and whilst this can be reduced using the `-Xss` flag, setting it too low will lead to a runtime `StackOverflowError`, at this point you need to increase stack size, or re-write the code to use less stack space (e.g. remove recursive method calls).

#### Slow scheduling
Another side effect of using OS threads is the performance impact introduced by OS kernel based thread scheduling (i.e. assigning hardware resources to them).   

There will typically be more Java threads than there are OS threads (cores), this is where the OS scheduler comes in, it will will try and give each thread a fair share of the CPU time. If a thread goes into a wait state (e.g. waiting for a database call), the thread will be marked as paused and a different thread is allocated to the OS thread - this is known as a context switch.

TODO: so what is cost here? 

#### Slow creation
Creating Threads  requires allocating OS resources, which is slow.

Things which cost a lot to create are typically pooled, and that's why we have the various flavours of `ExecutorService`. And if there's not enough threads in the pool to support the all the concurrent tasks that needs to run at a single point in time? They generally have to wait in line, with the risk of been rejected. Asynchronous programming is an approach to address this, but not everything fits this approach.  

#### So what can we do?
This is probably as good as it gets, a cached thread pool that will create new threads as needed, but will reuse previously constructed threads when they are available - the only limitation for a cached thread pool is the available system resources, as we've observed already when we ran out of native OS threads.

```java
static final ExecutorService service = Executors.newCachedThreadPool();

static void go(Runnable r) {
    service.submit(r);
}
```
    
### How does go do it better?
TODO

### How can we make Java more like go?
So what can we do about this? Use a better JVM :) And there is one out there, it's under development, and its called [Project Loom](https://wiki.openjdk.java.net/display/loom/Main) and is part of the OpenJDK community.

![`Loom`](img/loom.png)

Its mission is to:
 
_... drastically reduce the effort of writing, maintaining, and observing high-throughput concurrent applications that make the best use of available hardware._

..the feature we want is a [_virtual thread_](http://cr.openjdk.java.net/~rpressler/loom/loom/sol1_part1.html):
```java
static void go(Runnable r) {
   Thread.startVirtualThread(r);
}
```
A virtual thread is a (type of) `java.lang.Thread` — it's not a wrapper around an OS thread, but a Java object, known to the VM, and under the direct control of the Java runtime which, unlike the original Java platform thread's 1:1 mapping, maps (multiplexes) multiple virtual threads onto an OS thread:

![`Threads`](img/threads.png)

(image sourced from [inside.java](https://inside.java/2020/07/29/loom-accentodev/))

This means it's possible to create many more virtual threads (millions versus thousands on my mac OS), and each is relatively cheap to create - this also means we no longer need thread pools - if we need a thread, we just create a new one.

The JVM, and not the OS scheduler, controls the execution, suspending and resuming of virtual threads, which means lower context switching costs/faster task switching, leading to greater performance. This design also enables re-sizeble stacks that can change over time, leading to a much smaller memory footprint.

### Lets do some testing!

#### Thread volume
Lets start off with understanding difference in number of threads that can be created. 

As noted earlier, my mac OS limits a user to the creation of 2048 OS threads, so I can only create 2048 Java Threads. So if I create 2048 virtual threads, how many OS threads will they be multiplex onto? The answer is .. 26 for my run, as can be seen from this JConsole:  
  
![`loom`](img/jconsole_loom.png)

(these tests we're performed using [ThreadNumberTest.java](/src/test/java/com/github/stehrn/go/ThreadNumberTest.java))

#### Thread xxx


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


# Resources
Some articles that helped contribute to content in this post

* https://www.baeldung.com/openjdk-project-loom
* https://developers.redhat.com/blog/2019/06/19/project-loom-lightweight-java-threads/
* https://rcoh.me/posts/why-you-can-have-a-million-go-routines-but-only-1000-java-threads/