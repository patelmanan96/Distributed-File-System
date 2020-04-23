To use this application, you will need to navigate to the ./jars folder. And follow the bellow steps to get started.

1. Open a terminal and run the following command to start a server.

	java -jar fileserver.jar

	* You will be asked to enter a port number [7000-7004] after starting. Please pick a port.
	* You can repeat the above step to start multiple servers on separate terminal. Currently our application supports upto 5 servers.

2. Next step is to open another terminal and start the Load balancer using the following command.

	java -jar loadbalancer.jar <port number>

	* You can pass a port number as an argument if you choose to, otherwise it defaults to port 9001

3. The next step is to start a client. Open a new terminal and run the following command.
	
	java - jar client.jar
	
	* You can repeat the above step to start multiple clients on separate terminal. 
	* Once a client connects to a server through the load balancer you will be presented with the following menu:
			1. List all files		
			2. Upload file
			3. Download file
			4. Delete file
			5. Rename file
			6. Exit
	* Select an option by entering a number and proceed according to the instructions.
