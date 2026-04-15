package com.hawk.grpc_server.service;

import com.hawk.grpc_server.entity.EmployeeEntity;
import com.hawk.grpc_server.proto.Employee;
import com.hawk.grpc_server.proto.EmployeeByIdReq;
import com.hawk.grpc_server.proto.EmployeeServiceGrpc;
import com.hawk.grpc_server.proto.Employees;
import com.hawk.grpc_server.proto.Empty;
import com.hawk.grpc_server.repository.EmployeeRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class EmployeeServiceImpl extends EmployeeServiceGrpc.EmployeeServiceImplBase {

    private final EmployeeRepository employeeRepository;

    public EmployeeServiceImpl(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    public void getEmployeeById(EmployeeByIdReq request, StreamObserver<Employee> responseObserver) {
        employeeRepository.findById(request.getId())
                .map(this::toProto)
                .ifPresentOrElse(employee -> {
                    responseObserver.onNext(employee);
                    responseObserver.onCompleted();
                }, () -> responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Employee not found for id " + request.getId())
                        .asRuntimeException()));
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
