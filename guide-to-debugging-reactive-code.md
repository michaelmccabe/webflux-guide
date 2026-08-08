# Guide to Debugging Reactive Java Code

Debugging reactive code (Project Reactor / Spring WebFlux) is notoriously challenging because execution jumps across threads and stack traces originate from scheduler loops rather than the code path that assembled the stream. 

This guide details best practices, tools, and programmatic patterns to debug reactive pipelines effectively.

---

## 1. The Reactive Stack Trace Problem
In synchronous code, a stack trace points directly to the line that failed. In reactive code:
- The stream is **assembled** on one thread (assembly time).
- The stream is **subscribed to** and **executed** on other threads (execution time).
If an exception occurs at execution time, the stack trace only shows scheduler loop details, completely missing your application class lines.

---

## 2. Dynamic Assembly Tracing (`Hooks.onOperatorDebug`)
To map execution errors back to assembly-time source code lines, enable operator debugging. 

### Usage:
Add this inside the main application startup class or inside a static block:
```java
import reactor.core.publisher.Hooks;

public class PaymentServiceApplication {
    public static void main(String[] args) {
        Hooks.onOperatorDebug(); // Enable assembly tracing
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
```

### Result:
When an error occurs, Reactor appends an **assembly trace** to the stack trace:
```text
java.lang.ArithmeticException: / by zero
	at ... (runtime execution thread details)
Assembly trace:
	Flux.map ──> at com.example.reactiveguide.service.PaymentService.createPayment(PaymentService.java:44)
```

> [!WARNING]
> `Hooks.onOperatorDebug()` has a **significant performance penalty** because it captures stack traces on every stream operator instantiation. **Do not use it in production.**

### Production Alternative: Reactor Debug Agent
For production-safe assembly tracing, use the Java Agent `reactor-tools`. It modifies bytecode at class loading time and has zero overhead.
1. Add the dependency:
   ```xml
   <dependency>
       <groupId>io.projectreactor</groupId>
       <artifactId>reactor-tools</artifactId>
       <scope>runtime</scope>
   </dependency>
   ```
2. Initialize it at startup:
   ```java
   import reactor.tools.agent.ReactorDebugAgent;
   
   public static void main(String[] args) {
       ReactorDebugAgent.init();
       SpringApplication.run(PaymentServiceApplication.class, args);
   }
   ```

---

## 3. Detecting Blocking Calls (BlockHound)
A single blocking call inside a Reactor event loop thread (like `Schedulers.parallel()` or Netty) can serialize execution and destroy application performance. **BlockHound** is a Java agent that detects blocking calls at runtime.

### 1. Add Dependency:
```xml
<dependency>
    <groupId>io.projectreactor.tools</groupId>
    <artifactId>blockhound</artifactId>
    <version>1.0.8.RELEASE</version>
    <scope>test</scope>
</dependency>
```

### 2. Initialize in Tests or App Startup:
```java
import reactor.blockhound.BlockHound;

public class PaymentServiceApplication {
    static {
        BlockHound.install(); // Must be installed before any class loading
    }
}
```

### 3. Example Block Detection:
If you try to call a blocking method (like `Thread.sleep` or synchronous file read) inside a reactive thread:
```java
Mono.delay(Duration.ofMillis(1))
    .doOnNext(x -> {
        try {
            Thread.sleep(100); // Blocking call!
        } catch (InterruptedException e) {}
    })
```
BlockHound will intercept it and throw a runtime exception:
```text
reactor.blockhound.BlockingOperationError: Blocking call! java.lang.Thread.sleep
    at java.lang.Thread.sleep(Native Method)
    at com.example.reactiveguide.service.PaymentService.lambda$0(PaymentService.java:45)
```

---

## 4. Programmatic Debugging Operators

### A. The `.log()` Operator
Insert `.log()` to dump reactive signals (`onSubscribe`, `request`, `onNext`, `onError`, `onComplete`) directly to the console logger.

```java
public Mono<PaymentResponse> getPaymentById(Long id) {
    return paymentRepository.findById(id)
            .log("db-payment-fetch") // Names the log trace
            .map(paymentMapper::toResponse);
}
```
**Console Output:**
```text
16:15:30.123 [reactor-tcp-1] INFO db-payment-fetch - | onSubscribe([Mono] FlatMap)
16:15:30.124 [reactor-tcp-1] INFO db-payment-fetch - | request(unbounded)
16:15:30.128 [reactor-tcp-1] INFO db-payment-fetch - | onNext(Payment(id=1, amount=150.00))
16:15:30.129 [reactor-tcp-1] INFO db-payment-fetch - | onComplete()
```

### B. The `.checkpoint()` Operator
Inserts a custom named checkpoint into the reactive pipeline. If an error is propagated down the stream, the checkpoint description is printed.

```java
public Mono<PaymentResponse> process(PaymentRequest request) {
    return paymentRepository.save(payment)
            .checkpoint("Save payment in Database failed")
            .map(paymentMapper::toResponse);
}
```
If `save()` fails, the log will output:
```text
Encountered error in source: ...
Error has been observed at the following site(s):
	|_ Save payment in Database failed
```

---

## 5. Testing with `StepVerifier`
Instead of using system mock verification, use Reactor's `StepVerifier` to verify event signals step-by-step.

```java
import reactor.test.StepVerifier;

@Test
void verifyPaymentStream() {
    Flux<String> paymentStatuses = Flux.just("PENDING", "COMPLETED");

    StepVerifier.create(paymentStatuses)
            .expectNext("PENDING")
            .expectNext("COMPLETED")
            .verifyComplete(); // Asserts stream finishes with complete signal
}
```
