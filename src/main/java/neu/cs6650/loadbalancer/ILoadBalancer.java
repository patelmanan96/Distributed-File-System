package neu.cs6650.loadbalancer;

/**
 * This class represents a load balancer for a distributed file server.
 */
public interface ILoadBalancer {

  /**
   * Method to fetch the server port of the server to be connected to the requesting client.
   *
   * @return server port
   */
  int getServerPort();
}
