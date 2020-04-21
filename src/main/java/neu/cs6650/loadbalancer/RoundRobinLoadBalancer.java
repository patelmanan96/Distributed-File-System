package neu.cs6650.loadbalancer;

import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import neu.cs6650.server.DistributedFileServer;
import neu.cs6650.utils.Constants;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * This class represents a Round Robin Load balancer. This load balancer selects a server port from
 * a given list in a round robin fashion.
 */
public class RoundRobinLoadBalancer extends UnicastRemoteObject implements ILoadBalancer {

  private static Integer position = 0;
  private static Logger logger = LogManager.getLogger(RoundRobinLoadBalancer.class);

  public RoundRobinLoadBalancer() throws RemoteException {

  }

  @Override
  public int getServerPort() {
    int target;
    synchronized (position) {
      if (position > (Constants.PORTS.length - 1)) {
        position = 0;
      }
      target = Constants.PORTS[position];
      position++;
    }
    return target;
  }

/*  private List<Integer> getLiveServers() {
    for (int p : Constants.PORTS) {
      try {
        Registry discoveredRegistry = LocateRegistry.getRegistry(Constants.IP, p);
        for (String serverId : discoveredRegistry.list()) {
          try {
            DistributedFileServer discoveredServer =
                (DistributedFileServer) discoveredRegistry.lookup(serverId);
            if (!currentServerId.equals(discoveredServer.getServerID())) {
              isDiscovered = true;
              server.setStorage(discoveredServer.getStorage());
              discoveredServer.registerNewServer(currentServerId, server);
              server.registerNewServer(discoveredServer.getServerID(), discoveredServer);
            }
          } catch (ConnectException e) {
            System.out.println("Unable to connect: " + e.getMessage());
          }
        }
        if (isDiscovered) {
          break;
        }
      } catch (Exception e) {
        System.out.println("Waiting for other servers...");
      }

    }
  }*/

  public static void main(String[] args) {
    Configurator.setLevel(logger.getName(), Level.ALL);
    logger.info("Round-Robin Load balancer started...");

    int portNumber;
    if (args.length < 1) {
      logger.warn("No port provided, using default port: {}", Constants.DEFAULT_LB_PORT);
      portNumber = Constants.DEFAULT_LB_PORT;
    } else {
      portNumber = Integer.parseInt(args[0]);
    }

    try {
      ILoadBalancer loadBalancer = new RoundRobinLoadBalancer();
      Registry registry = LocateRegistry.createRegistry(portNumber);
      registry.bind(Constants.RR_LOAD_BALANCER, loadBalancer);
      logger.info("Object binding is done");

    } catch (Exception e) {
      logger.error(e.getMessage());
    }
  }
}
