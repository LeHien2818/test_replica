package auth.replica;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReplicaApplication {

	public static void main(String[] args) {
		System.out.println("This is not the first time running");
		SpringApplication.run(ReplicaApplication.class, args);
	}

}
