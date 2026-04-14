package com.example.hawk.grpc.repo;

import com.example.hawk.grpc.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Integer> {
    Employee findEmployeeById(int id);

    List<Employee> findAllBy();
}
