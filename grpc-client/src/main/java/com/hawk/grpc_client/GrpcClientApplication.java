package com.hawk.grpc_client;

import com.hawk.grpc_client.proto.EmployeeServiceGrpc;
import com.hawk.grpc_client.service.EmployeeService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.ImportGrpcClients;

@SpringBootApplication
@ImportGrpcClients(types = EmployeeServiceGrpc.EmployeeServiceBlockingStub.class)
public class GrpcClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(GrpcClientApplication.class, args);
	}

	@Bean
	@ConditionalOnProperty(name = "app.runner.enabled", havingValue = "true", matchIfMissing = true)
	CommandLineRunner commandLineRunner(EmployeeService employeeService) {
		return args -> System.out.println("GRPC Client Response : " + employeeService.getEmployeeById(1));
	}
}
