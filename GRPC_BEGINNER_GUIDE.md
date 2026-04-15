# gRPC Beginner Guide For This Repo

This note explains the exact gRPC setup used in this project.

It is written for a complete beginner, so it focuses on:

- what each folder does
- how the proto file is used
- how the client and server connect
- why some common errors happened
- how to run the project in the correct order

## Project Structure

- `grpc-server`
  This is the gRPC server.
  It reads employee data from PostgreSQL and exposes gRPC methods.

- `grpc-client`
  This is the gRPC client.
  It connects to the server and calls gRPC methods.

## Proto File

The proto file defines the contract between client and server.

Current client proto:
- `grpc-client/src/main/proto/Employee.proto`

Current server proto:
- `grpc-server/src/main/proto/Employee.proto`

Important proto options:

```proto
option java_package = "com.hawk.grpc_client.proto";
option java_outer_classname = "EmployeeProto";
option java_multiple_files = true;
```

What this means:

- `java_package`
  Generated Java files will be created in this package.

- `java_outer_classname`
  Main generated container class name.

- `java_multiple_files = true`
  Generates separate Java classes like `Employee`, `EmployeeByIdReq`, `EmployeeServiceGrpc`, etc.

## Very Important Package Rule

Java package names cannot contain `-`.

This is invalid:

```text
com.hawk.grpc-server
```

This is valid:

```text
com.hawk.grpc_server
```

Use `_` if you need a separator in a Java package name.

## What Gets Generated From Proto

From the proto file, Maven generates classes like:

- `Employee`
- `EmployeeByIdReq`
- `EmployeeResponse`
- `Employees`
- `EmployeeServiceGrpc`

You do not write these classes manually.
They are generated during Maven build.

## Client And Server Connection

Current client config:

```yaml
spring:
  grpc:
    client:
      default-channel:
        address: static://localhost:9090
        negotiation-type: plaintext
```

Current server config:

```yaml
spring:
  grpc:
    server:
      port: 9090
```

This means:

- the server listens on port `9090`
- the client calls `localhost:9090`

So both sides already match.

## Database Config

The server also needs PostgreSQL:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/grpc
    username: postgres
    password: postgres
```

So before the server can work, PostgreSQL must be running and the `grpc` database must exist.

## How The Server Works

The server implements methods from the generated base class:

```java
public class EmployeeServiceImpl extends EmployeeServiceGrpc.EmployeeServiceImplBase
```

Example unary RPC:

- client sends one request
- server sends one response

Example server-streaming RPC:

- client sends one request
- server sends many responses

In this repo:

- `getEmployeeById(...)` is unary
- `getAllEmployee(...)` is unary
- `subscribeEmployeeById(...)` is server-streaming

## How The Client Works

The client uses generated stubs.

There are different stub types:

- `EmployeeServiceBlockingStub`
  Good for simple unary calls.

- `EmployeeServiceStub`
  Async stub. Needed for streaming with `StreamObserver`.

Current client registration uses:

```java
@ImportGrpcClients(types = {
    EmployeeServiceGrpc.EmployeeServiceBlockingStub.class,
    EmployeeServiceGrpc.EmployeeServiceStub.class
})
```

This is important.
If you inject a stub into a Spring bean, that stub must be registered first.

## Why `@GrpcClient(...)` Did Not Work

This project uses the official Spring gRPC starter:

- `org.springframework.grpc:spring-grpc-client-spring-boot-starter`

In this setup, the correct pattern is `@ImportGrpcClients(...)`.

The tutorial you followed was likely for a different library that uses `@GrpcClient(...)`.

So the problem was not your idea.
It was a library mismatch.

## Why `new EmployeeService()` Was Wrong

This is a very common beginner mistake.

If you do this:

```java
EmployeeService employeeService = new EmployeeService();
```

Spring does not manage that object.
That means constructor injection and bean injection do not happen.

Correct approach:

- let Spring create the bean
- inject it into your runner or controller

Example:

```java
CommandLineRunner commandLineRunner(EmployeeService employeeService) {
    return args -> System.out.println(employeeService.getEmployeeById(1));
}
```

## Unary RPC vs Streaming RPC

### Unary RPC

Unary means:

- one request
- one response

Example:

```java
EmployeeResponse response = employeeServiceBlockingStub.getEmployeeById(request);
```

### Server-Streaming RPC

Server-streaming means:

- one request
- many responses

If you use the async stub:

```java
employeeServiceStub.subscribeEmployeeById(request, new StreamObserver<EmployeeResponse>() {
    @Override
    public void onNext(EmployeeResponse value) {
        System.out.println(value);
    }

    @Override
    public void onError(Throwable t) {
        t.printStackTrace();
    }

    @Override
    public void onCompleted() {
        System.out.println("done");
    }
});
```

Important:

- async streaming does not return `EmployeeResponse`
- it returns results through `onNext(...)`

## `repeated` vs `stream`

This is one of the easiest proto concepts to confuse at the beginning.

The short version:

- `repeated`
  means a list inside one message

- `stream`
  means many messages sent over one RPC call

### `repeated` Example

```proto
message Employees {
  repeated Employee employees = 1;
}
```

This means:

- one protobuf message
- inside that one message is a list of `Employee`

Think of it like:

```text
Employees
  -> [Employee, Employee, Employee]
```

### `stream` Example

```proto
rpc subscribeEmployeeById(EmployeeByIdReq) returns (stream EmployeeResponse);
```

This means:

- client sends one request
- server sends many `EmployeeResponse` messages one by one

Think of it like:

```text
EmployeeResponse
EmployeeResponse
EmployeeResponse
EmployeeResponse
```

Each one is a separate message on the wire.

### Why Your Earlier Design Was Different

Earlier you had a shape like this:

```proto
message EmployeesRequest {
  repeated Employee employees = 1;
}

rpc bulkEmployeeCreation(stream EmployeesRequest) returns (...);
```

That means:

- each streamed message already contains a list of employees
- and then the RPC streams many of those messages

So that is:

```text
stream of batches
```

not:

```text
stream of single employees
```

### Clean Mental Model

Use `repeated` when:

- you want many values inside one request or one response
- example: `getAllEmployee` returning one `Employees` message

Use `stream` when:

- you want values to arrive one by one over time
- example: `subscribeEmployeeById`

Use `stream Employee` when:

- the client should send one employee at a time
- example: your new `bulkEmployeeCreation`

### Quick Comparison

One message with many items:

```proto
message Employees {
  repeated Employee employees = 1;
}
```

Many messages over time:

```proto
rpc subscribeEmployeeById(EmployeeByIdReq) returns (stream EmployeeResponse);
```

Many single employees sent by client:

```proto
rpc bulkEmployeeCreation(stream Employee) returns (BulkEmployeeCreationResponse);
```

## Common Errors You Already Hit

### 1. Invalid package name

Cause:
- using `grpc-server` inside Java package name

Fix:
- use `grpc_server`

### 2. `@GrpcClient(...)` did not work

Cause:
- tutorial used a different gRPC starter library

Fix:
- use `@ImportGrpcClients(...)`

### 3. Null stub

Cause:
- service object created manually with `new`

Fix:
- let Spring create and inject the bean

### 4. Missing bean for `EmployeeServiceStub`

Cause:
- async stub was injected, but only blocking stub was registered

Fix:
- register both stubs in `@ImportGrpcClients(...)`

### 5. `Connection refused`

Cause:
- client started before server
- or server was not running on port `9090`

Fix:
- start PostgreSQL
- start `grpc-server`
- then start `grpc-client`

### 6. `NOT_FOUND: Employee not found for id 1`

Cause:
- the client asked for employee id `1`
- that row did not exist in the database

Fix:
- insert a record first
- or call `getAllEmployee()`
- or handle `NOT_FOUND` properly in the client

## Clean Mental Model

Think of the flow like this:

1. Proto defines request/response/message shape.
2. Maven generates Java classes from proto.
3. Server implements the generated service base class.
4. Client uses generated stubs to call server methods.
5. Server may read from database before sending proto response.

## How To Run This Project

1. Start PostgreSQL.
2. Make sure database `grpc` exists.
3. Start `grpc-server`.
4. Start `grpc-client`.

If the client runs first, it will fail because no server is listening yet.

## Useful Commands

Build server:

```bash
cd grpc-server
./mvnw test
```

Build client:

```bash
cd grpc-client
./mvnw test
```

Run server:

```bash
cd grpc-server
./mvnw spring-boot:run
```

Run client:

```bash
cd grpc-client
./mvnw spring-boot:run
```

## Beginner Advice

- Do not panic when a stacktrace is long.
  Usually the real problem is in the first meaningful error line.

- For Spring bean issues, check:
  Is the bean registered?
  Is Spring creating the object?
  Am I calling `new` manually?

- For gRPC issues, check:
  Is the server running?
  Is the port correct?
  Is the proto regenerated?
  Am I using the correct stub type?

- For database issues, check:
  Is PostgreSQL running?
  Does the table have data?
  Does the requested id actually exist?

## Final Summary

For this repo, remember these 5 rules:

1. Proto generates Java code.
2. Server implements generated service base classes.
3. Client uses generated stubs.
4. Official Spring gRPC here uses `@ImportGrpcClients(...)`, not `@GrpcClient(...)`.
5. Start database, then server, then client.
