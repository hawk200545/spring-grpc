package com.hawk.grpc_client;

import com.hawk.grpc_client.proto.BulkEmployeeCreationResponse;
import com.hawk.grpc_client.proto.Employee;
import com.hawk.grpc_client.proto.EmployeeServiceGrpc;
import java.util.List;
import com.hawk.grpc_client.service.EmployeeService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.ImportGrpcClients;

import java.util.List;

@SpringBootApplication
@ImportGrpcClients(types = {
		EmployeeServiceGrpc.EmployeeServiceBlockingStub.class,
		EmployeeServiceGrpc.EmployeeServiceStub.class
})
public class GrpcClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(GrpcClientApplication.class, args);
	}

	@Bean
	@ConditionalOnProperty(name = "app.runner.enabled", havingValue = "true", matchIfMissing = true)
	CommandLineRunner commandLineRunner(EmployeeService employeeService) {

		return args -> {
			System.out.println("Unary gRCP : " + employeeService.getEmployeeById(1));
			System.out.println("Server Side Streaming initiating...");
			employeeService.subscribeEmployeeById(1);
			System.out.println("Client Streaming initiating...");
			String fileLocation = args.length > 0 ? args[0] : "employees.txt";
			System.out.println("Bulk employee import started from: " + fileLocation);
			BulkEmployeeCreationResponse response = employeeService.bulkEmployeeCreationFromFile(fileLocation);
			System.out.println("Bulk employee import completed. Created employees: "
					+ response.getCreatedEmployees());

			System.out.println("Bidirectional Streaming (liveEmployeeCreation) initiating...");
			List<Integer> createdIds = employeeService.liveEmployeeCreation(List.of(
					Employee.newBuilder().setName("Alice").setAge(28).build(),
					Employee.newBuilder().setName("Bob").setAge(34).build(),
					Employee.newBuilder().setName("Carol").setAge(25).build()
			));
			System.out.println("Live employee creation completed. Created IDs: " + createdIds);
		};
	}
}
