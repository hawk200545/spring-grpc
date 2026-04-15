package com.hawk.grpc_server.service;

import com.hawk.grpc_server.entity.EmployeeEntity;
import com.hawk.grpc_server.proto.*;
import com.hawk.grpc_server.repository.EmployeeRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

import java.util.concurrent.TimeUnit;

@GrpcService
public class EmployeeServiceImpl extends EmployeeServiceGrpc.EmployeeServiceImplBase {

    private final EmployeeRepository employeeRepository;

    public EmployeeServiceImpl(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    public StreamObserver<Employee> liveEmployeeCreation(StreamObserver<EmployeeCreationResponse> responseObserver) {
        return new StreamObserver<Employee>() {
            @Override
            public void onNext(Employee value) {
                EmployeeEntity employeeEntity = new EmployeeEntity();
                employeeEntity.setAge(value.getAge());
                employeeEntity.setName(value.getName());
                EmployeeEntity responseEntity = employeeRepository.save(employeeEntity);
                EmployeeCreationResponse employeeCreationResponse = EmployeeCreationResponse.newBuilder()
                        .setEmployeeId(responseEntity.getId())
                        .build();
                responseObserver.onNext(employeeCreationResponse);
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("liveEmployeeCreation stream error: " + t.getMessage());
                responseObserver.onError(t); // propagate — closes client latch via onError callback
            }

            @Override
            public void onCompleted() {
                System.out.println("The server has completed the bi-directional streaming");
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void getEmployeeById(EmployeeByIdReq request, StreamObserver<EmployeeResponse> responseObserver) {
        employeeRepository.findById(request.getId())
                .map(this::toEmployeeResponse)
                .ifPresentOrElse(
                        response -> {
                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                        },
                        () -> responseObserver.onError(
                                Status.NOT_FOUND
                                        .withDescription("Employee not found for id " + request.getId())
                                        .asRuntimeException()
                        )
                );
    }


    // stream data from server to client
    @Override
    public void subscribeEmployeeById(EmployeeByIdReq request, StreamObserver<EmployeeResponse> responseObserver) {
        try {
            for (int i = 1; i < 11; i++) {
                EmployeeResponse response = employeeRepository.findById(i)
                        .map(this::toEmployeeResponse)
                        .orElse(null);
                if (response == null) {
                    responseObserver.onError(Status.NOT_FOUND
                            .withDescription("Employee not found for id " + i)
                            .asRuntimeException());
                    return;
                }

                responseObserver.onNext(response);
                TimeUnit.SECONDS.sleep(1);
            }
            responseObserver.onCompleted();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Streaming interrupted")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    private EmployeeResponse toEmployeeResponse(EmployeeEntity employee) {
        return EmployeeResponse.newBuilder()
                .setEmployee(Employee.newBuilder()
                        .setId(employee.getId())
                        .setName(employee.getName() == null ? "" : employee.getName())
                        .setAge(employee.getAge())
                        .build())
                .build();
    }

    @Override
    public StreamObserver<Employee> bulkEmployeeCreation(StreamObserver<BulkEmployeeCreationResponse> responseObserver) {
        return new StreamObserver<>() {
            private int createdEmployees = 0;

            @Override
            public void onNext(Employee value) {
                EmployeeEntity employeeEntity = new EmployeeEntity();
                employeeEntity.setName(value.getName());
                employeeEntity.setAge(value.getAge());
                employeeRepository.save(employeeEntity);
                createdEmployees++;
            }

            @Override
            public void onError(Throwable t) {
                System.out.println(t.getMessage());
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(BulkEmployeeCreationResponse.newBuilder()
                        .setCreatedEmployees(createdEmployees)
                        .build());
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void getAllEmployee(Empty request, StreamObserver<Employees> responseObserver) {
        Employees response = Employees.newBuilder()
                .addAllEmployees(employeeRepository.findAll().stream().map(this::toProto).toList())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private Employee toProto(EmployeeEntity employee) {
        return Employee.newBuilder()
                .setId(employee.getId() == null ? 0 : employee.getId())
                .setName(employee.getName() == null ? "" : employee.getName())
                .setAge(employee.getAge())
                .build();
    }
}
