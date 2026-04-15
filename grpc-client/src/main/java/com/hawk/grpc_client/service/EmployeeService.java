package com.hawk.grpc_client.service;

import com.hawk.grpc_client.proto.Employee;
import com.hawk.grpc_client.proto.EmployeeByIdReq;
import com.hawk.grpc_client.proto.EmployeeServiceGrpc;
import org.springframework.stereotype.Service;

@Service
public class EmployeeService {

    private final EmployeeServiceGrpc.EmployeeServiceBlockingStub employeeServiceBlockingStub;

    public EmployeeService(EmployeeServiceGrpc.EmployeeServiceBlockingStub employeeServiceBlockingStub) {
        this.employeeServiceBlockingStub = employeeServiceBlockingStub;
    }

    public Employee getEmployeeById(int id){
        EmployeeByIdReq employeeByIdReq = EmployeeByIdReq.newBuilder().setId(id).build();
        return employeeServiceBlockingStub.getEmployeeById(employeeByIdReq);
    }
}
