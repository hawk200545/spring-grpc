package com.example.hawk.grpc.service;

import com.example.hawk.grpc.Employee;
import com.example.hawk.grpc.EmployeeByIdReq;
import com.example.hawk.grpc.EmployeeServiceGrpc;
import com.example.hawk.grpc.Employees;
import com.example.hawk.grpc.Empty;
import com.example.hawk.grpc.repo.EmployeeRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class EmployeeServiceImpl extends EmployeeServiceGrpc.EmployeeServiceImplBase {
    private final EmployeeRepository employeeRepository;

    public EmployeeServiceImpl(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    public void getEmployeeById(EmployeeByIdReq request, StreamObserver<Employee> responseObserver) {
        com.example.hawk.grpc.entity.Employee employee = employeeRepository.findEmployeeById(request.getId());
        if (employee == null) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription("Employee not found for id " + request.getId())
                            .asRuntimeException());
            return;
        }

        responseObserver.onNext(toProto(employee));
        responseObserver.onCompleted();
    }

    @Override
    public void getAllEmployee(Empty request, StreamObserver<Employees> responseObserver) {
        List<com.example.hawk.grpc.entity.Employee> employees = employeeRepository.findAllBy();
        Employees response = Employees.newBuilder()
                .addAllEmployees(employees.stream().map(this::toProto).toList())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private Employee toProto(com.example.hawk.grpc.entity.Employee employee) {
        return Employee.newBuilder()
                .setId(employee.getId())
                .setName(employee.getName() == null ? "" : employee.getName())
                .setAge(employee.getAge())
                .build();
    }
}
