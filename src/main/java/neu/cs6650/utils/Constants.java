package neu.cs6650.utils;

public class Constants {

  public static final String DELETE = "DELETE";
  public static final String GET = "GET";
  public static final String PUT = "PUT";
  public static final int TIMEOUT = 10000;
  public static final int EXIT = 6;
  public static final int[] PORTS = new int[]{7401, 7402, 7403, 7404, 7405};
  public static final String IP = "127.0.0.1";
  public static final String SERVER_NAME = "PaxosServer";
  public static final int DEFAULT_PORT = 7401;
  public static final int DEFAULT_LB_PORT = 8400;
  public static final String RR_LOAD_BALANCER = "RoundRobinLoadBalancer";
  public static final String RANDOM_LOAD_BALANCER = "RandomLoadBalancer";


  private Constants() {

  }
}
