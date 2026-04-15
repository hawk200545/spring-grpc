package com.hawk.grpc_client.service;

import com.hawk.grpc_client.proto.BulkEmployeeCreationResponse;
import com.hawk.grpc_client.proto.Employee;
import com.hawk.grpc_client.proto.EmployeeByIdReq;
import com.hawk.grpc_client.proto.EmployeeCreationResponse;
import com.hawk.grpc_client.proto.EmployeeResponse;
import com.hawk.grpc_client.proto.EmployeeServiceGrpc;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class EmployeeService {

    private final EmployeeServiceGrpc.EmployeeServiceBlockingStub employeeServiceBlockingStub;
    private final EmployeeServiceGrpc.EmployeeServiceStub employeeServiceStub;
    private final ResourceLoader resourceLoader;

    public EmployeeService(EmployeeServiceGrpc.EmployeeServiceBlockingStub employeeServiceBlockingStub,
            EmployeeServiceGrpc.EmployeeServiceStub employeeServiceStub,
            ResourceLoader resourceLoader) {
        this.employeeServiceBlockingStub = employeeServiceBlockingStub;
        this.employeeServiceStub = employeeServiceStub;
        this.resourceLoader = resourceLoader;
    }

    public EmployeeResponse getEmployeeById(int id){
        EmployeeByIdReq employeeByIdReq = EmployeeByIdReq.newBuilder().setId(id).build();
        return employeeServiceBlockingStub.getEmployeeById(employeeByIdReq);
    }

    public void subscribeEmployeeById(int id){
        EmployeeByIdReq employeeByIdReq = EmployeeByIdReq.newBuilder().setId(id).build();
        Iterator<EmployeeResponse> responses = employeeServiceBlockingStub.subscribeEmployeeById(employeeByIdReq);

        while (responses.hasNext()) {
            EmployeeResponse value = responses.next();
            System.out.println("Employee : " + value.getEmployee().getName()
                    + " Age : " + value.getEmployee().getAge()
                    + " id : " + value.getEmployee().getId());
        }

        System.out.println("Server streaming completed");
    }

    public BulkEmployeeCreationResponse bulkEmployeeCreation(List<Employee> employees) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<BulkEmployeeCreationResponse> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        StreamObserver<Employee> requestObserver = employeeServiceStub.bulkEmployeeCreation(new StreamObserver<>() {
            @Override
            public void onNext(BulkEmployeeCreationResponse value) {
                responseRef.set(value);
            }

            @Override
            public void onError(Throwable t) {
                errorRef.set(t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });

        for (Employee employee : employees) {
            requestObserver.onNext(employee);
        }
        requestObserver.onCompleted();

        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for bulk employee creation response");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for bulk employee creation response", e);
        }

        if (errorRef.get() != null) {
            throw new StatusRuntimeException(io.grpc.Status.fromThrowable(errorRef.get()));
        }

        return responseRef.get();
    }

    public BulkEmployeeCreationResponse bulkEmployeeCreationFromFile(String resourceLocation) {
        return bulkEmployeeCreation(parseEmployees(resourceLocation));
    }

    /**
     * Bidirectional streaming: sends each employee and receives its generated ID immediately.
     * Unlike bulkEmployeeCreation, the server responds per-employee in real time.
     */
    public List<Integer> liveEmployeeCreation(List<Employee> employees) {
        CountDownLatch latch = new CountDownLatch(1);
        List<Integer> createdIds = new ArrayList<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        int expectedResponses = employees.size();
        AtomicInteger responseCount = new AtomicInteger(0);

        StreamObserver<Employee> requestObserver = employeeServiceStub.liveEmployeeCreation(new StreamObserver<>() {
            @Override
            public void onNext(EmployeeCreationResponse response) {
                System.out.println("Live created employee with id: " + response.getEmployeeId());
                createdIds.add(response.getEmployeeId());
                // Release as soon as all expected responses arrive —
                // don't depend solely on onCompleted which may not fire in all gRPC versions.
                if (responseCount.incrementAndGet() >= expectedResponses) {
                    latch.countDown();
                }
            }

            @Override
            public void onError(Throwable t) {
                errorRef.set(t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown(); // backup: no-op if onNext already released
            }
        });

        for (Employee employee : employees) {
            requestObserver.onNext(employee);
        }
        requestObserver.onCompleted();

        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for live employee creation");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during live employee creation", e);
        }

        if (errorRef.get() != null) {
            throw new StatusRuntimeException(io.grpc.Status.fromThrowable(errorRef.get()));
        }

        return createdIds;
    }

    public List<Integer> liveEmployeeCreationFromFile(String resourceLocation) {
        return liveEmployeeCreation(parseEmployees(resourceLocation));
    }

    private List<Employee> parseEmployees(String resourceLocation) {
        Resource resource = resourceLoader.getResource(resourceLocation);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Employee file not found: " + resourceLocation);
        }

        List<Employee> employees = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                employees.add(parseEmployeeLine(trimmed, lineNumber));
            }
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to read employee file: " + resourceLocation, e);
        }

        if (employees.isEmpty()) {
            throw new IllegalArgumentException("No employees found in file: " + resourceLocation);
        }

        return employees;
    }

    private Employee parseEmployeeLine(String line, int lineNumber) {
        String[] parts = line.split(",", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Invalid employee entry at line " + lineNumber + ". Expected format: name,age");
        }

        String name = parts[0].trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Employee name is empty at line " + lineNumber);
        }

        int age;
        try {
            age = Integer.parseInt(parts[1].trim());
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid employee age at line " + lineNumber, e);
        }

        if (age < 0) {
            throw new IllegalArgumentException("Employee age cannot be negative at line " + lineNumber);
        }

        return Employee.newBuilder()
                .setName(name)
                .setAge(age)
                .build();
    }



}
