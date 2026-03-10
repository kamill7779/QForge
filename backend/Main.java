import ai.z.openapi.ZhipuAiClient;
public class Main {
  public static void main(String[] args) {
    System.out.println(java.lang.reflect.Modifier.isFinal(ZhipuAiClient.class.getModifiers()));
    System.out.println(ZhipuAiClient.class.getSuperclass().getName());
  }
}
