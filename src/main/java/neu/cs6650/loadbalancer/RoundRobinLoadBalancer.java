package neu.cs6650.loadbalancer;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import neu.cs6650.utils.Constants;

/**
 * This class represents a Round Robin Load balancer. This load balancer selects a server port from
 * a given list in a round robin fashion.
 */
public class RoundRobinLoadBalancer extends UnicastRemoteObject implements ILoadBalancer {

  private static Integer position = 0;
  private static Logger logger = Logger.getLogger(RoundRobinLoadBalancer.class.getName());
  private LinkedHashSet<Integer> liveServers;

  public RoundRobinLoadBalancer() throws RemoteException {
    this.liveServers = new LinkedHashSet<>();
  }

  @Override
  public int getServerPort() {
    int target;
    List<Integer> servers = this.getLiveServers();
    if (servers.isEmpty()) {
      throw new IllegalStateException("No live servers detected.");
    }
    synchronized (position) {
      if (position > (servers.size() - 1)) {
        position = 0;
      }
      target = servers.get(position);
      position++;
    }
    return target;
  }

  private List<Integer> getLiveServers() {
    List<Integer> foundServers = new ArrayList<>();
    for (int p : Constants.PORTS) {
      try {
        Registry discoveredRegistry = LocateRegistry.getRegistry(Constants.IP, p);
        if (discoveredRegistry.list().length > 0) {
          foundServers.add(p);
        }
      } catch (Exception e) {
        System.out.println("Waiting for other servers...");
      }
    }
    this.liveServers.removeIf(s -> !foundServers.contains(s));
    this.liveServers.addAll(foundServers);
    return new ArrayList<>(this.liveServers);
  }

  public static void main(String[] args) {
    logger.info("Round-Robin Load balancer started...");

    int portNumber;
    if (args.length < 1) {
      logger.log(Level.WARNING, "No port provided, using default port: {0}",
          Constants.DEFAULT_LB_PORT);
      portNumber = Constants.DEFAULT_LB_PORT;
    } else {
      portNumber = Integer.parseInt(args[0]);
    }

    try {
      ILoadBalancer loadBalancer = new RoundRobinLoadBalancer();
      Registry registry = LocateRegistry.createRegistry(portNumber);
      registry.bind(Constants.RR_LOAD_BALANCER, loadBalancer);
      logger.log(Level.INFO, "Object binding is done");

    } catch (Exception e) {
      logger.log(Level.SEVERE, e.getMessage());
    }
  }
}
