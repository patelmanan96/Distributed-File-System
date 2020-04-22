package neu.cs6650.utils;

public class Constants {

  public static final int EXIT = 6;
  public static final int[] PORTS = new int[]{7000, 7001, 7002, 7003, 7004};
  public static final String IP = "127.0.0.1";
  public static final String SERVER_NAME = "FileServer";
  public static final int DEFAULT_LB_PORT = 9001;
  public static final String RR_LOAD_BALANCER = "RoundRobinLoadBalancer";
  public static final String RANDOM_LOAD_BALANCER = "RandomLoadBalancer";
  public static final int RETRY_COUNT = 3;


  private Constants() {

  }
}
